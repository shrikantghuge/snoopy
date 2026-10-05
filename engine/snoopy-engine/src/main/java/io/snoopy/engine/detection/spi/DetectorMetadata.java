package io.snoopy.engine.detection.spi;

import io.snoopy.core.domain.Severity;

import java.util.List;

public record DetectorMetadata(
        String id,
        String name,
        String secretType,
        String provider,
        Severity defaultSeverity,
        int defaultConfidence,
        List<String> keywords,
        String regexPattern,
        double entropyThreshold
) {}
