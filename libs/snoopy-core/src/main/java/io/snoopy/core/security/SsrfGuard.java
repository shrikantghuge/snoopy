package io.snoopy.core.security;

import io.snoopy.core.spi.connector.ConnectorException;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * SSRF protection for all outbound connector traffic (spec 42 / threat T5).
 *
 * <ul>
 *   <li>Only {@code https} by default ({@code http} only when {@code allowPrivateNetwork} is enabled, for local tests).</li>
 *   <li>Host must match the explicit allowlist (exact host or {@code *.suffix} wildcard).</li>
 *   <li>Host is resolved and every resolved IP is re-validated: loopback, link-local, site-local, multicast
 *       and any-local addresses are rejected unless {@code allowPrivateNetwork} is true.</li>
 * </ul>
 */
public final class SsrfGuard {

    private final List<String> allowedHosts;
    private final boolean allowPrivateNetwork;

    public SsrfGuard(Set<String> allowedHosts, boolean allowPrivateNetwork) {
        this.allowedHosts = allowedHosts.stream().map(h -> h.toLowerCase(Locale.ROOT)).toList();
        this.allowPrivateNetwork = allowPrivateNetwork;
    }

    public void validate(URI uri) {
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!scheme.equals("https") && !(scheme.equals("http") && allowPrivateNetwork)) {
            throw blocked("scheme not allowed: " + scheme);
        }
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw blocked("missing host");
        }
        host = host.toLowerCase(Locale.ROOT);
        if (!isAllowedHost(host)) {
            throw blocked("host not in allowlist: " + host);
        }
        if (!allowPrivateNetwork) {
            try {
                for (InetAddress address : InetAddress.getAllByName(host)) {
                    if (isPrivate(address)) {
                        throw blocked("host resolves to a private/loopback address: " + host);
                    }
                }
            } catch (UnknownHostException e) {
                throw new ConnectorException(ConnectorException.Kind.CONFIGURATION, "unknown host: " + host, -1, e);
            }
        }
    }

    public boolean isAllowedHost(String host) {
        String h = host.toLowerCase(Locale.ROOT);
        for (String allowed : allowedHosts) {
            if (allowed.startsWith("*.")) {
                String suffix = allowed.substring(1); // ".atlassian.net"
                if (h.endsWith(suffix) && h.length() > suffix.length()) return true;
            } else if (allowed.equals(h)) {
                return true;
            }
        }
        return false;
    }

    static boolean isPrivate(InetAddress a) {
        return a.isLoopbackAddress() || a.isLinkLocalAddress() || a.isSiteLocalAddress()
                || a.isAnyLocalAddress() || a.isMulticastAddress()
                || isUniqueLocalV6(a) || isCarrierGradeNat(a) || isMetadataAddress(a);
    }

    private static boolean isUniqueLocalV6(InetAddress a) {
        byte[] b = a.getAddress();
        return b.length == 16 && (b[0] & 0xfe) == 0xfc;
    }

    private static boolean isCarrierGradeNat(InetAddress a) {
        byte[] b = a.getAddress();
        return b.length == 4 && (b[0] & 0xff) == 100 && (b[1] & 0xc0) == 64;
    }

    private static boolean isMetadataAddress(InetAddress a) {
        byte[] b = a.getAddress();
        return b.length == 4 && (b[0] & 0xff) == 169 && (b[1] & 0xff) == 254;
    }

    private static ConnectorException blocked(String msg) {
        return new ConnectorException(ConnectorException.Kind.BLOCKED, "SSRF guard: " + msg);
    }
}
