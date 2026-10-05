package io.snoopy.connector.confluence;

import com.fasterxml.jackson.databind.JsonNode;
import io.snoopy.core.domain.Actor;
import io.snoopy.core.domain.ChangeKind;
import io.snoopy.core.domain.ObjectType;
import io.snoopy.core.domain.SourceType;
import io.snoopy.core.security.ResilientHttpClient;
import io.snoopy.core.spi.connector.*;

import java.net.URI;
import java.time.Instant;
import java.util.*;

public class ConfluenceConnector implements SourceConnector {

    @Override
    public SourceType type() {
        return SourceType.CONFLUENCE_CLOUD;
    }

    @Override
    public Set<Capability> capabilities() {
        return Set.of(
                Capability.CURRENT_CONTENT,
                Capability.HISTORY,
                Capability.VERSIONS,
                Capability.COMMENTS,
                Capability.ATTACHMENTS,
                Capability.AUTHOR_METADATA,
                Capability.INCREMENTAL
        );
    }

    @Override
    public Map<String, String> coverage() {
        return Map.of("reachableHistory", "COMPLETE", "currentContent", "COMPLETE");
    }

    @Override
    public ConnectionHealth checkConnection(ConnectionConfig config) {
        ResilientHttpClient client = createClient(config);
        try {
            JsonNode json = client.getJson(config.baseUrl() + "/wiki/rest/api/user/current");
            String displayName = json.path("displayName").asText("unknown");
            return ConnectionHealth.ok("Authenticated as Confluence user: " + displayName, Map.of("user", displayName));
        } catch (Exception e) {
            return ConnectionHealth.failed("Confluence connection failed: " + e.getMessage());
        }
    }

    @Override
    public List<ScopeItem> discoverScopes(ConnectionConfig config) {
        ResilientHttpClient client = createClient(config);
        List<ScopeItem> items = new ArrayList<>();
        try {
            JsonNode json = client.getJson(config.baseUrl() + "/wiki/api/v2/spaces?limit=100");
            JsonNode results = json.path("results");
            if (results.isArray()) {
                results.forEach(space -> {
                    String key = space.path("key").asText();
                    String name = space.path("name").asText();
                    String url = config.baseUrl() + "/wiki/spaces/" + key;
                    items.add(new ScopeItem(key, "CONFLUENCE_SPACE", name, url));
                });
            }
        } catch (Exception ignored) {}
        return items;
    }

    @Override
    public void scan(ScanContext context, ContentSink sink) {
        ResilientHttpClient client = createClient(context.config());

        for (ScopeSelection scope : context.scopes()) {
            if (sink.isCancelled()) return;

            String spaceKey = scope.externalId();
            String pagesUrl = context.config().baseUrl() + "/wiki/api/v2/spaces/" + spaceKey + "/pages?body-format=storage&limit=50";

            while (pagesUrl != null && !pagesUrl.isBlank()) {
                if (sink.isCancelled()) return;

                JsonNode pageResponse;
                try {
                    pageResponse = client.getJson(pagesUrl);
                } catch (Exception e) {
                    sink.failure(spaceKey, spaceKey, "Failed to fetch Confluence pages", e);
                    break;
                }

                JsonNode pages = pageResponse.path("results");
                if (pages.isArray()) {
                    for (JsonNode page : pages) {
                        scanPage(client, context, spaceKey, page, scope, sink);
                    }
                }

                String nextLink = pageResponse.path("_links").path("next").asText(null);
                pagesUrl = nextLink != null && !nextLink.isBlank() ? context.config().baseUrl() + nextLink : null;
            }

            sink.checkpoint(spaceKey, Instant.now().toString());
        }
    }

    private void scanPage(ResilientHttpClient client, ScanContext context, String spaceKey, JsonNode page, ScopeSelection scope, ContentSink sink) {
        String pageId = page.path("id").asText();
        String title = page.path("title").asText();
        String body = page.path("body").path("storage").path("value").asText("");
        int currentVersion = page.path("version").path("number").asInt(1);
        Actor author = parseActor(page.path("version").path("author"));
        Instant timestamp = parseTime(page.path("version").path("createdAt").asText());

        // 1. Current page version
        ContentPayload payload = ContentPayload.builder()
                .sourceType(SourceType.CONFLUENCE_CLOUD)
                .scopeKey(spaceKey)
                .externalObjectId("page:" + pageId + ":v" + currentVersion)
                .seriesId("page:" + pageId)
                .objectType(ObjectType.CONFLUENCE_PAGE)
                .version(String.valueOf(currentVersion))
                .sequence(currentVersion)
                .changeKind(ChangeKind.PRESENT)
                .current(true)
                .title(title)
                .path(spaceKey + "/" + title)
                .sourceUrl(context.config().baseUrl() + "/wiki/spaces/" + spaceKey + "/pages/" + pageId)
                .author(author)
                .timestamp(timestamp)
                .text(body)
                .meta(Meta.SPACE_KEY, spaceKey)
                .meta(Meta.PAGE_ID, pageId)
                .meta(Meta.VERSION_NUMBER, String.valueOf(currentVersion))
                .build();

        sink.accept(payload);

        // 2. Scan historical page versions if history scan is enabled
        if (scope.scanHistory() && currentVersion > 1) {
            scanPageVersions(client, context, spaceKey, pageId, title, currentVersion, sink);
        }

        // 3. Scan page comments if enabled
        if (scope.scanComments()) {
            scanComments(client, context, spaceKey, pageId, sink);
        }
    }

    private void scanPageVersions(ResilientHttpClient client, ScanContext context, String spaceKey, String pageId, String title, int latestVersion, ContentSink sink) {
        try {
            String versionsUrl = context.config().baseUrl() + "/wiki/api/v2/pages/" + pageId + "/versions?body-format=storage&limit=50";
            JsonNode json = client.getJson(versionsUrl);
            JsonNode versions = json.path("results");
            if (versions.isArray()) {
                for (JsonNode ver : versions) {
                    int verNum = ver.path("number").asInt();
                    if (verNum == latestVersion) continue; // Skip latest (already scanned as current)

                    Actor verAuthor = parseActor(ver.path("author"));
                    Instant verTime = parseTime(ver.path("createdAt").asText());
                    String verBody = ver.path("body").path("storage").path("value").asText("");

                    ContentPayload payload = ContentPayload.builder()
                            .sourceType(SourceType.CONFLUENCE_CLOUD)
                            .scopeKey(spaceKey)
                            .externalObjectId("page:" + pageId + ":v" + verNum)
                            .seriesId("page:" + pageId)
                            .objectType(ObjectType.CONFLUENCE_PAGE_VERSION)
                            .version(String.valueOf(verNum))
                            .sequence(verNum)
                            .changeKind(ChangeKind.ADDED)
                            .current(false)
                            .title(title + " (Version " + verNum + ")")
                            .path(spaceKey + "/" + title + "/v" + verNum)
                            .author(verAuthor)
                            .timestamp(verTime)
                            .text(verBody)
                            .meta(Meta.SPACE_KEY, spaceKey)
                            .meta(Meta.PAGE_ID, pageId)
                            .meta(Meta.VERSION_NUMBER, String.valueOf(verNum))
                            .build();

                    sink.accept(payload);
                }
            }
        } catch (Exception e) {
            sink.failure(spaceKey, pageId + ":versions", "Failed to scan page versions", e);
        }
    }

    private void scanComments(ResilientHttpClient client, ScanContext context, String spaceKey, String pageId, ContentSink sink) {
        try {
            String commentsUrl = context.config().baseUrl() + "/wiki/api/v2/pages/" + pageId + "/footer-comments?body-format=storage&limit=50";
            JsonNode json = client.getJson(commentsUrl);
            JsonNode comments = json.path("results");
            if (comments.isArray()) {
                for (JsonNode comment : comments) {
                    String commentId = comment.path("id").asText();
                    Actor author = parseActor(comment.path("version").path("author"));
                    Instant created = parseTime(comment.path("version").path("createdAt").asText());
                    String body = comment.path("body").path("storage").path("value").asText("");

                    ContentPayload payload = ContentPayload.builder()
                            .sourceType(SourceType.CONFLUENCE_CLOUD)
                            .scopeKey(spaceKey)
                            .externalObjectId("page:" + pageId + ":comment:" + commentId)
                            .seriesId("page:" + pageId + ":comment:" + commentId)
                            .objectType(ObjectType.CONFLUENCE_COMMENT)
                            .version(commentId)
                            .sequence(created.toEpochMilli())
                            .changeKind(ChangeKind.PRESENT)
                            .current(true)
                            .title("Page " + pageId + " Comment #" + commentId)
                            .author(author)
                            .timestamp(created)
                            .text(body)
                            .meta(Meta.SPACE_KEY, spaceKey)
                            .meta(Meta.PAGE_ID, pageId)
                            .meta(Meta.COMMENT_ID, commentId)
                            .build();

                    sink.accept(payload);
                }
            }
        } catch (Exception e) {
            sink.failure(spaceKey, pageId + ":comments", "Failed to scan comments", e);
        }
    }

    private ResilientHttpClient createClient(ConnectionConfig config) {
        String username = config.credential("username");
        String token = config.credential("token");
        String basic = Base64.getEncoder().encodeToString(((username != null ? username : "") + ":" + (token != null ? token : "")).getBytes());

        URI uri = URI.create(config.baseUrl());
        String host = uri.getHost() != null ? uri.getHost() : "*";

        return ResilientHttpClient.builder()
                .allowedHosts(Set.of(host, "*.atlassian.net"))
                .allowPrivateNetwork(config.flag("allowPrivateNetwork"))
                .header("Authorization", "Basic " + basic)
                .header("Accept", "application/json")
                .header("User-Agent", "Snoopy-Platform")
                .build();
    }

    private Actor parseActor(JsonNode node) {
        if (node == null || node.isMissingNode()) return null;
        String id = node.path("accountId").asText("unknown");
        String name = node.path("displayName").asText(id);
        String email = node.path("emailAddress").asText(null);
        return Actor.of(id, name, email);
    }

    private Instant parseTime(String text) {
        if (text == null || text.isBlank()) return Instant.now();
        try {
            return Instant.parse(text);
        } catch (Exception e) {
            return Instant.now();
        }
    }
}
