package io.snoopy.engine.detection.spi;

import io.snoopy.core.domain.Severity;

import java.util.Map;

/**
 * An unverified secret detection candidate found within a chunk of text.
 * Ephemeral output of detection. Plain text secret is NOT stored permanently.
 */
public record SecretCandidate(
        String detectorId,
        String secretType,
        String provider,
        int startOffset,
        int endOffset,
        int lineNumber,
        String matchedValue,
        String contextSnippet,
        int confidence,
        Severity severity,
        double entropy,
        Map<String, String> metadata
) {
    public SecretCandidate {
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}
