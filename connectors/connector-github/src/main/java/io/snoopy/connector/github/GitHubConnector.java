package io.snoopy.connector.github;

import io.snoopy.core.domain.SourceType;
import io.snoopy.core.security.ResilientHttpClient;
import io.snoopy.core.spi.connector.*;
import org.eclipse.jgit.api.Git;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.*;

public class GitHubConnector implements SourceConnector {

    private static final Logger log = LoggerFactory.getLogger(GitHubConnector.class);
    private final JGitHistoryScanner historyScanner = new JGitHistoryScanner();

    @Override
    public SourceType type() {
        return SourceType.GITHUB;
    }

    @Override
    public Set<Capability> capabilities() {
        return Set.of(
                Capability.CURRENT_CONTENT,
                Capability.HISTORY,
                Capability.VERSIONS,
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
        String token = config.credential("token");
        String baseUrl = config.baseUrl().isBlank() ? "https://api.github.com" : config.baseUrl();

        ResilientHttpClient client = ResilientHttpClient.builder()
                .allowedHosts(Set.of("api.github.com", "github.com", "*.github.com"))
                .allowPrivateNetwork(config.flag("allowPrivateNetwork"))
                .header("Authorization", "Bearer " + (token != null ? token : ""))
                .header("User-Agent", "Snoopy-Platform")
                .build();

        try {
            var json = client.getJson(baseUrl + "/user");
            String login = json.path("login").asText("unknown");
            return ConnectionHealth.ok("Authenticated as GitHub user: " + login, Map.of("login", login));
        } catch (Exception e) {
            return ConnectionHealth.failed("GitHub connection check failed: " + e.getMessage());
        }
    }

    @Override
    public List<ScopeItem> discoverScopes(ConnectionConfig config) {
        String token = config.credential("token");
        String org = config.setting("organization", "");
        String baseUrl = config.baseUrl().isBlank() ? "https://api.github.com" : config.baseUrl();

        ResilientHttpClient client = ResilientHttpClient.builder()
                .allowedHosts(Set.of("api.github.com", "github.com", "*.github.com"))
                .allowPrivateNetwork(config.flag("allowPrivateNetwork"))
                .header("Authorization", "Bearer " + (token != null ? token : ""))
                .header("User-Agent", "Snoopy-Platform")
                .build();

        List<ScopeItem> scopes = new ArrayList<>();
        String url = org.isBlank() ? baseUrl + "/user/repos?per_page=100" : baseUrl + "/orgs/" + org + "/repos?per_page=100";

        try {
            var json = client.getJson(url);
            if (json.isArray()) {
                json.forEach(repo -> {
                    String fullName = repo.path("full_name").asText();
                    String htmlUrl = repo.path("html_url").asText();
                    scopes.add(new ScopeItem(fullName, "REPOSITORY", fullName, htmlUrl));
                });
            }
        } catch (Exception e) {
            log.warn("Failed to discover GitHub scopes: {}", e.getMessage());
        }

        return scopes;
    }

    @Override
    public void scan(ScanContext context, ContentSink sink) {
        String token = context.config().credential("token");

        for (ScopeSelection scope : context.scopes()) {
            if (sink.isCancelled()) return;

            String repoFullName = scope.externalId();
            // Local git repo shortcut for testing / offline mode
            File localGitDir = new File(repoFullName);
            if (localGitDir.exists() && new File(localGitDir, ".git").exists()) {
                historyScanner.scanRepository(localGitDir, repoFullName, scope.scanHistory(), sink);
                continue;
            }

            // Remote clone via JGit to context workDir
            try {
                File cloneDir = new File(context.workDir().toFile(), repoFullName.replace('/', '_'));
                String cloneUrl = "https://github.com/" + repoFullName + ".git";
                if (token != null && !token.isBlank()) {
                    cloneUrl = "https://x-access-token:" + token + "@github.com/" + repoFullName + ".git";
                }

                log.info("Cloning repository {} into {}", repoFullName, cloneDir);
                try (Git git = Git.cloneRepository()
                        .setURI(cloneUrl)
                        .setDirectory(cloneDir)
                        .setCloneAllBranches(true)
                        .call()) {
                    historyScanner.scanRepository(cloneDir, repoFullName, scope.scanHistory(), sink);
                }
            } catch (Exception e) {
                sink.failure(repoFullName, repoFullName, "Failed remote git clone & scan", e);
            }
        }
    }
}
