package io.snoopy.core.spi.connector;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Everything a connector needs to perform one scan.
 *
 * @param config      resolved connection config
 * @param scopes      selected scopes; if empty the connector scans all discoverable scopes
 * @param mode        FULL or INCREMENTAL
 * @param cursors     previously persisted cursors keyed by scope key (see {@link ContentSink#checkpoint})
 * @param options     limits
 * @param workDir     private working directory for this scan (e.g. git mirrors); may be deleted afterwards
 */
public record ScanContext(
        ConnectionConfig config,
        List<ScopeSelection> scopes,
        ScanMode mode,
        Map<String, String> cursors,
        ScanOptions options,
        Path workDir) {

    public ScanContext {
        scopes = scopes == null ? List.of() : List.copyOf(scopes);
        cursors = cursors == null ? Map.of() : Map.copyOf(cursors);
        options = options == null ? ScanOptions.defaults() : options;
    }

    /** Cursor for a scope, only when running incrementally; {@code null} means "scan everything". */
    public String cursorFor(String scopeKey) {
        return mode == ScanMode.INCREMENTAL ? cursors.get(scopeKey) : null;
    }
}
