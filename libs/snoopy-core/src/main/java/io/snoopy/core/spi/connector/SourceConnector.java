package io.snoopy.core.spi.connector;

import io.snoopy.core.domain.SourceType;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Source connector SPI (spec section 8).
 *
 * <p>Phase 1 uses a <b>push model with checkpoints</b>: the connector walks its source and pushes normalized
 * {@link ContentPayload}s into a {@link ContentSink}, calling {@link ContentSink#checkpoint} after each durable
 * unit (repository, issue page, page batch). This keeps connectors simple while remaining resumable and
 * incremental, and maps 1:1 to the spec's discoverCurrent/discoverHistorical/fetchContent split.
 *
 * <p>Connectors MUST NOT contain detection logic.
 */
public interface SourceConnector {

    SourceType type();

    Set<Capability> capabilities();

    /** Honest coverage description shown in the UI (spec 48), e.g. {@code reachableHistory -> COMPLETE}. */
    Map<String, String> coverage();

    ConnectionHealth checkConnection(ConnectionConfig config);

    /** Lists scannable scopes (repositories, Jira projects, Confluence spaces). */
    List<ScopeItem> discoverScopes(ConnectionConfig config);

    /**
     * Walks the selected scopes and emits content. Must honour {@link ScanContext#mode()},
     * {@link ScanContext#cursorFor(String)} for incremental scans, the scope flags, and
     * {@link ContentSink#isCancelled()}.
     *
     * <p>Per-object failures must be reported via {@link ContentSink#failure} and must not abort the scope.
     */
    void scan(ScanContext context, ContentSink sink);
}
