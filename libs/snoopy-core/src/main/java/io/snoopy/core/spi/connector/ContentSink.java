package io.snoopy.core.spi.connector;

/**
 * Receives normalized content from a connector. Implemented by the platform pipeline.
 */
public interface ContentSink {

    /** Process one content payload (extraction, detection, correlation, persistence). */
    void accept(ContentPayload payload);

    /**
     * Persist the cursor for a scope. Call only after all payloads up to that cursor were accepted
     * (spec 12: "persist connector cursor after durable processing, not before").
     */
    void checkpoint(String scopeKey, String cursor);

    /** Record a non-fatal failure for an object; scanning continues. Never include secrets in the reason. */
    void failure(String scopeKey, String objectRef, String reason, Throwable error);

    /** Progress hint: number of objects discovered in a scope. */
    default void discovered(String scopeKey, long count) {}

    /** Connectors should poll this between objects and stop promptly when true. */
    default boolean isCancelled() {
        return false;
    }
}
