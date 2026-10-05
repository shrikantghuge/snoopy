package io.snoopy.core.spi.connector;

import io.snoopy.core.domain.SourceType;

import java.util.Map;

/**
 * Resolved connection configuration handed to a connector.
 *
 * @param connectionId id of the SourceConnection in the platform
 * @param type         source type
 * @param baseUrl      e.g. {@code https://api.github.com}, {@code https://acme.atlassian.net}
 * @param authType     e.g. {@code TOKEN}, {@code BASIC}, {@code NONE}
 * @param credentials  resolved secrets (e.g. {@code token}, {@code username}). Transient, never logged.
 * @param settings     non-secret connector settings (e.g. {@code organization}, {@code allowPrivateNetwork})
 */
public record ConnectionConfig(
        String connectionId,
        SourceType type,
        String baseUrl,
        String authType,
        Map<String, String> credentials,
        Map<String, String> settings) {

    public ConnectionConfig {
        credentials = credentials == null ? Map.of() : Map.copyOf(credentials);
        settings = settings == null ? Map.of() : Map.copyOf(settings);
    }

    public String credential(String key) {
        return credentials.get(key);
    }

    public String setting(String key, String defaultValue) {
        return settings.getOrDefault(key, defaultValue);
    }

    public boolean flag(String key) {
        return Boolean.parseBoolean(settings.getOrDefault(key, "false"));
    }

    @Override
    public String toString() {
        return "ConnectionConfig{id=" + connectionId + ", type=" + type + ", baseUrl=" + baseUrl
                + ", authType=" + authType + ", credentials=[REDACTED], settings=" + settings + "}";
    }
}
