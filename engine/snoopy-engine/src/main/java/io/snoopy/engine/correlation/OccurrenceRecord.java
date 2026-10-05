package io.snoopy.engine.correlation;

import io.snoopy.core.domain.Actor;

import java.time.Instant;

public record OccurrenceRecord(
        String occurrenceFingerprint,
        String credentialFingerprint,
        String sourceType,
        String scopeKey,
        String externalObjectId,
        String seriesId,
        String version,
        boolean isCurrent,
        int lineNumber,
        String maskedSecret,
        Actor author,
        Instant timestamp,
        String sourceUrl,
        String contextSnippet
) {}
