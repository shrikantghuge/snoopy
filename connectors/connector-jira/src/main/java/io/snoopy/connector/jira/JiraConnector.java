package io.snoopy.connector.jira;

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

public class JiraConnector implements SourceConnector {

    @Override
    public SourceType type() {
        return SourceType.JIRA_CLOUD;
    }

    @Override
    public Set<Capability> capabilities() {
        return Set.of(
                Capability.CURRENT_CONTENT,
                Capability.HISTORY,
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
            JsonNode json = client.getJson(config.baseUrl() + "/rest/api/3/myself");
            String displayName = json.path("displayName").asText("unknown");
            return ConnectionHealth.ok("Authenticated as Jira user: " + displayName, Map.of("user", displayName));
        } catch (Exception e) {
            return ConnectionHealth.failed("Jira connection failed: " + e.getMessage());
        }
    }

    @Override
    public List<ScopeItem> discoverScopes(ConnectionConfig config) {
        ResilientHttpClient client = createClient(config);
        List<ScopeItem> items = new ArrayList<>();
        try {
            JsonNode json = client.getJson(config.baseUrl() + "/rest/api/3/project/search");
            JsonNode values = json.path("values");
            if (values.isArray()) {
                values.forEach(proj -> {
                    String key = proj.path("key").asText();
                    String name = proj.path("name").asText();
                    String selfUrl = config.baseUrl() + "/browse/" + key;
                    items.add(new ScopeItem(key, "JIRA_PROJECT", name, selfUrl));
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

            String projectKey = scope.externalId();
            int startAt = 0;
            int maxResults = 50;

            while (true) {
                if (sink.isCancelled()) return;

                String jql = "project = '" + projectKey + "' ORDER BY updated DESC";
                String cursor = context.cursorFor(projectKey);
                if (cursor != null && !cursor.isBlank()) {
                    jql = "project = '" + projectKey + "' AND updated >= '" + cursor + "' ORDER BY updated DESC";
                }

                String searchUrl = context.config().baseUrl() + "/rest/api/3/search?jql="
                        + java.net.URLEncoder.encode(jql, java.nio.charset.StandardCharsets.UTF_8)
                        + "&startAt=" + startAt + "&maxResults=" + maxResults
                        + "&fields=summary,description,reporter,assignee,updated,created";

                JsonNode searchResult;
                try {
                    searchResult = client.getJson(searchUrl);
                } catch (Exception e) {
                    sink.failure(projectKey, projectKey, "Failed to search Jira issues", e);
                    break;
                }

                JsonNode issues = searchResult.path("issues");
                if (!issues.isArray() || issues.size() == 0) break;

                for (JsonNode issue : issues) {
                    scanIssue(client, context, projectKey, issue, scope, sink);
                }

                startAt += issues.size();
                int total = searchResult.path("total").asText().isEmpty() ? 0 : searchResult.path("total").asInt();
                if (startAt >= total) break;
            }

            sink.checkpoint(projectKey, Instant.now().toString());
        }
    }

    private void scanIssue(ResilientHttpClient client, ScanContext context, String projectKey, JsonNode issue, ScopeSelection scope, ContentSink sink) {
        String issueKey = issue.path("key").asText();
        JsonNode fields = issue.path("fields");

        Actor reporter = parseActor(fields.path("reporter"));
        Actor assignee = parseActor(fields.path("assignee"));
        Instant updated = parseTime(fields.path("updated").asText());

        String summary = fields.path("summary").asText("");
        String description = fields.path("description").asText("");

        // 1. Issue summary and description
        ContentPayload payload = ContentPayload.builder()
                .sourceType(SourceType.JIRA_CLOUD)
                .scopeKey(projectKey)
                .externalObjectId(issueKey + ":fields")
                .seriesId(issueKey + ":description")
                .objectType(ObjectType.JIRA_ISSUE_FIELD)
                .version(String.valueOf(updated.toEpochMilli()))
                .sequence(updated.toEpochMilli())
                .changeKind(ChangeKind.PRESENT)
                .current(true)
                .title(issueKey + " - " + summary)
                .sourceUrl(context.config().baseUrl() + "/browse/" + issueKey)
                .author(reporter)
                .relatedActor(Meta.ROLE_ASSIGNEE, assignee)
                .timestamp(updated)
                .text(summary + "\n\n" + description)
                .meta(Meta.ISSUE_KEY, issueKey)
                .meta(Meta.PROJECT_KEY, projectKey)
                .build();

        sink.accept(payload);

        // 2. Scan issue comments if enabled
        if (scope.scanComments()) {
            scanComments(client, context, projectKey, issueKey, sink);
        }

        // 3. Scan issue changelog history if enabled
        if (scope.scanHistory()) {
            scanChangelog(client, context, projectKey, issueKey, sink);
        }
    }

    private void scanComments(ResilientHttpClient client, ScanContext context, String projectKey, String issueKey, ContentSink sink) {
        try {
            JsonNode json = client.getJson(context.config().baseUrl() + "/rest/api/3/issue/" + issueKey + "/comment");
            JsonNode comments = json.path("comments");
            if (comments.isArray()) {
                for (JsonNode comment : comments) {
                    String commentId = comment.path("id").asText();
                    Actor author = parseActor(comment.path("author"));
                    Instant created = parseTime(comment.path("created").asText());
                    String body = comment.path("body").asText("");

                    ContentPayload payload = ContentPayload.builder()
                            .sourceType(SourceType.JIRA_CLOUD)
                            .scopeKey(projectKey)
                            .externalObjectId(issueKey + ":comment:" + commentId)
                            .seriesId(issueKey + ":comment:" + commentId)
                            .objectType(ObjectType.JIRA_COMMENT)
                            .version(commentId)
                            .sequence(created.toEpochMilli())
                            .changeKind(ChangeKind.PRESENT)
                            .current(true)
                            .title(issueKey + " Comment #" + commentId)
                            .author(author)
                            .timestamp(created)
                            .text(body)
                            .meta(Meta.ISSUE_KEY, issueKey)
                            .meta(Meta.COMMENT_ID, commentId)
                            .build();

                    sink.accept(payload);
                }
            }
        } catch (Exception e) {
            sink.failure(projectKey, issueKey + ":comments", "Failed to scan comments", e);
        }
    }

    private void scanChangelog(ResilientHttpClient client, ScanContext context, String projectKey, String issueKey, ContentSink sink) {
        try {
            JsonNode json = client.getJson(context.config().baseUrl() + "/rest/api/3/issue/" + issueKey + "/changelog");
            JsonNode values = json.path("values");
            if (values.isArray()) {
                for (JsonNode change : values) {
                    String changeId = change.path("id").asText();
                    Actor author = parseActor(change.path("author"));
                    Instant created = parseTime(change.path("created").asText());

                    JsonNode items = change.path("items");
                    if (items.isArray()) {
                        for (JsonNode item : items) {
                            String field = item.path("field").asText();
                            String fromString = item.path("fromString").asText("");
                            String toString = item.path("toString").asText("");

                            // Both old (from) and new (to) values can contain historical secrets!
                            String changeText = "Field " + field + " changed.\nOld Value: " + fromString + "\nNew Value: " + toString;

                            ContentPayload payload = ContentPayload.builder()
                                    .sourceType(SourceType.JIRA_CLOUD)
                                    .scopeKey(projectKey)
                                    .externalObjectId(issueKey + ":changelog:" + changeId + ":" + field)
                                    .seriesId(issueKey + ":changelog:" + field)
                                    .objectType(ObjectType.JIRA_CHANGELOG)
                                    .version(changeId)
                                    .sequence(created.toEpochMilli())
                                    .changeKind(ChangeKind.ADDED)
                                    .current(false) // Changelog entries represent historical states
                                    .title(issueKey + " Changelog for " + field)
                                    .author(author)
                                    .timestamp(created)
                                    .text(changeText)
                                    .meta(Meta.ISSUE_KEY, issueKey)
                                    .meta(Meta.FIELD, field)
                                    .build();

                            sink.accept(payload);
                        }
                    }
                }
            }
        } catch (Exception e) {
            sink.failure(projectKey, issueKey + ":changelog", "Failed to scan changelog", e);
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
        String id = node.path("accountId").asText(node.path("name").asText("unknown"));
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
