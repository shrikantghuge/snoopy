package io.snoopy.core.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.snoopy.core.spi.connector.ConnectorException;
import io.snoopy.core.spi.connector.ConnectorException.Kind;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Shared outbound HTTP client for connectors (spec 12):
 * <ul>
 *   <li>SSRF guard on every request <b>and every redirect hop</b></li>
 *   <li>Authorization header is dropped when a redirect leaves the original host (e.g. Jira media redirects)</li>
 *   <li>429 / 5xx retry with {@code Retry-After} support, exponential backoff (1s,2s,4s,8s) + jitter, bounded attempts</li>
 *   <li>connect/read timeouts, max response size</li>
 *   <li>typed {@link ConnectorException}s for 401/403/404</li>
 * </ul>
 * Never logs headers or bodies.
 */
public final class ResilientHttpClient {

    private static final Logger log = LoggerFactory.getLogger(ResilientHttpClient.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int MAX_REDIRECTS = 5;

    private final HttpClient client;
    private final SsrfGuard guard;
    private final Map<String, String> defaultHeaders;
    private final Duration readTimeout;
    private final int maxAttempts;
    private final long baseBackoffMillis;
    private final long maxBackoffMillis;
    private final Sleeper sleeper;

    /** Abstracted for tests. */
    public interface Sleeper {
        void sleep(long millis) throws InterruptedException;
    }

    public record HttpResult(int status, Map<String, List<String>> headers, byte[] body) {
        public Optional<String> header(String name) {
            for (Map.Entry<String, List<String>> e : headers.entrySet()) {
                if (e.getKey() != null && e.getKey().equalsIgnoreCase(name) && !e.getValue().isEmpty()) {
                    return Optional.of(e.getValue().get(0));
                }
            }
            return Optional.empty();
        }
    }

    private ResilientHttpClient(Builder b) {
        this.guard = new SsrfGuard(b.allowedHosts, b.allowPrivateNetwork);
        this.defaultHeaders = Map.copyOf(b.defaultHeaders);
        this.readTimeout = b.readTimeout;
        this.maxAttempts = b.maxAttempts;
        this.baseBackoffMillis = b.baseBackoffMillis;
        this.maxBackoffMillis = b.maxBackoffMillis;
        this.sleeper = b.sleeper;
        this.client = HttpClient.newBuilder()
                .connectTimeout(b.connectTimeout)
                .followRedirects(HttpClient.Redirect.NEVER) // redirects are validated manually
                .build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public SsrfGuard guard() {
        return guard;
    }

    public JsonNode getJson(String url) {
        HttpResult r = get(url, 20L * 1024 * 1024, Map.of("Accept", "application/json"));
        try {
            return MAPPER.readTree(r.body());
        } catch (IOException e) {
            throw new ConnectorException(Kind.UNKNOWN, "invalid JSON from " + safe(url), r.status(), e);
        }
    }

    public JsonNode postJson(String url, Object body) {
        try {
            byte[] payload = MAPPER.writeValueAsBytes(body);
            HttpResult r = execute("POST", url, payload, 20L * 1024 * 1024,
                    Map.of("Accept", "application/json", "Content-Type", "application/json"));
            return MAPPER.readTree(r.body());
        } catch (IOException e) {
            throw new ConnectorException(Kind.UNKNOWN, "invalid JSON from " + safe(url), -1, e);
        }
    }

    public HttpResult get(String url, long maxBytes, Map<String, String> extraHeaders) {
        return execute("GET", url, null, maxBytes, extraHeaders);
    }

    private HttpResult execute(String method, String url, byte[] body, long maxBytes, Map<String, String> extraHeaders) {
        int attempt = 0;
        while (true) {
            attempt++;
            try {
                HttpResult result = followRedirects(method, URI.create(url), body, maxBytes, extraHeaders);
                int s = result.status();
                if (s >= 200 && s < 300) {
                    return result;
                }
                if (s == 429 || s >= 500) {
                    if (attempt >= maxAttempts) {
                        throw new ConnectorException(s == 429 ? Kind.RATE_LIMITED : Kind.TRANSIENT,
                                "giving up after " + attempt + " attempts, status " + s + " for " + safe(url), s, null);
                    }
                    long wait = retryAfterMillis(result).orElse(backoff(attempt));
                    log.warn("HTTP {} from {} - retry {}/{} in {} ms", s, safe(url), attempt, maxAttempts, wait);
                    sleeper.sleep(wait);
                    continue;
                }
                if (s == 401) throw new ConnectorException(Kind.AUTHENTICATION, "authentication failed for " + safe(url), s, null);
                if (s == 403) {
                    // GitHub uses 403 for secondary rate limits.
                    if (result.header("x-ratelimit-remaining").map("0"::equals).orElse(false)
                            || result.header("retry-after").isPresent()) {
                        if (attempt >= maxAttempts) {
                            throw new ConnectorException(Kind.RATE_LIMITED, "rate limited: " + safe(url), s, null);
                        }
                        sleeper.sleep(retryAfterMillis(result).orElse(backoff(attempt)));
                        continue;
                    }
                    throw new ConnectorException(Kind.PERMISSION, "permission denied for " + safe(url), s, null);
                }
                if (s == 404) throw new ConnectorException(Kind.NOT_FOUND, "not found: " + safe(url), s, null);
                throw new ConnectorException(Kind.UNKNOWN, "unexpected HTTP " + s + " for " + safe(url), s, null);
            } catch (IOException e) {
                if (attempt >= maxAttempts) {
                    throw new ConnectorException(Kind.TRANSIENT, "I/O failure for " + safe(url) + ": " + e.getClass().getSimpleName(), -1, e);
                }
                sleepQuietly(backoff(attempt));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new ConnectorException(Kind.TRANSIENT, "interrupted", -1, e);
            }
        }
    }

    private HttpResult followRedirects(String method, URI uri, byte[] body, long maxBytes, Map<String, String> extraHeaders)
            throws IOException, InterruptedException {
        String originalHost = uri.getHost();
        URI current = uri;
        for (int hop = 0; hop <= MAX_REDIRECTS; hop++) {
            guard.validate(current);
            boolean sameHost = originalHost != null && originalHost.equalsIgnoreCase(current.getHost());
            HttpRequest.Builder rb = HttpRequest.newBuilder(current).timeout(readTimeout);
            Map<String, String> headers = new LinkedHashMap<>(defaultHeaders);
            headers.putAll(extraHeaders);
            headers.forEach((k, v) -> {
                if (sameHost || !k.equalsIgnoreCase("Authorization")) rb.header(k, v);
            });
            if (body != null && hop == 0) {
                rb.method(method, HttpRequest.BodyPublishers.ofByteArray(body));
            } else {
                rb.GET();
            }
            HttpResponse<InputStream> resp = client.send(rb.build(), HttpResponse.BodyHandlers.ofInputStream());
            int s = resp.statusCode();
            if (s == 301 || s == 302 || s == 303 || s == 307 || s == 308) {
                Optional<String> loc = resp.headers().firstValue("location");
                resp.body().close();
                if (loc.isEmpty()) throw new ConnectorException(Kind.UNKNOWN, "redirect without location", s, null);
                current = current.resolve(loc.get());
                continue;
            }
            byte[] bytes = readLimited(resp.body(), maxBytes);
            return new HttpResult(s, resp.headers().map(), bytes);
        }
        throw new ConnectorException(Kind.UNKNOWN, "too many redirects for " + safe(uri.toString()), -1, null);
    }

    private static byte[] readLimited(InputStream in, long maxBytes) throws IOException {
        try (in) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            long total = 0;
            int n;
            while ((n = in.read(buf)) != -1) {
                total += n;
                if (total > maxBytes) {
                    throw new ConnectorException(Kind.BLOCKED, "response exceeds max size " + maxBytes + " bytes");
                }
                out.write(buf, 0, n);
            }
            return out.toByteArray();
        }
    }

    private Optional<Long> retryAfterMillis(HttpResult r) {
        Optional<String> ra = r.header("retry-after");
        if (ra.isPresent()) {
            String v = ra.get().trim();
            try {
                return Optional.of(Math.min(Long.parseLong(v) * 1000L, maxBackoffMillis * 4));
            } catch (NumberFormatException ignored) {
                try {
                    long until = ZonedDateTime.parse(v, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli();
                    return Optional.of(Math.max(0, Math.min(until - System.currentTimeMillis(), maxBackoffMillis * 4)));
                } catch (Exception e) {
                    return Optional.empty();
                }
            }
        }
        Optional<String> reset = r.header("x-ratelimit-reset");
        if (reset.isPresent() && r.header("x-ratelimit-remaining").map("0"::equals).orElse(false)) {
            try {
                long until = Long.parseLong(reset.get().trim()) * 1000L;
                return Optional.of(Math.max(0, Math.min(until - System.currentTimeMillis(), maxBackoffMillis * 4)));
            } catch (NumberFormatException ignored) {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    long backoff(int attempt) {
        long exp = Math.min(maxBackoffMillis, baseBackoffMillis * (1L << Math.max(0, attempt - 1)));
        long jitter = exp <= 1 ? 0 : ThreadLocalRandom.current().nextLong(exp / 4 + 1);
        return exp + jitter;
    }

    private void sleepQuietly(long ms) {
        try {
            sleeper.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** Strips query strings (may carry tokens/signatures) before logging. */
    static String safe(String url) {
        int q = url.indexOf('?');
        return q < 0 ? url : url.substring(0, q) + "?…";
    }

    public static final class Builder {
        private java.util.Set<String> allowedHosts = java.util.Set.of();
        private boolean allowPrivateNetwork;
        private final Map<String, String> defaultHeaders = new LinkedHashMap<>();
        private Duration connectTimeout = Duration.ofSeconds(10);
        private Duration readTimeout = Duration.ofSeconds(30);
        private int maxAttempts = 5;
        private long baseBackoffMillis = 1000;
        private long maxBackoffMillis = 8000;
        private Sleeper sleeper = Thread::sleep;

        public Builder allowedHosts(java.util.Set<String> hosts) { this.allowedHosts = hosts; return this; }
        public Builder allowPrivateNetwork(boolean v) { this.allowPrivateNetwork = v; return this; }
        public Builder header(String k, String v) { this.defaultHeaders.put(k, v); return this; }
        public Builder connectTimeout(Duration d) { this.connectTimeout = d; return this; }
        public Builder readTimeout(Duration d) { this.readTimeout = d; return this; }
        public Builder maxAttempts(int n) { this.maxAttempts = n; return this; }
        public Builder backoff(long baseMillis, long maxMillis) { this.baseBackoffMillis = baseMillis; this.maxBackoffMillis = maxMillis; return this; }
        public Builder sleeper(Sleeper s) { this.sleeper = s; return this; }

        public ResilientHttpClient build() {
            return new ResilientHttpClient(this);
        }
    }
}
