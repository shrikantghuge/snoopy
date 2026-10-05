package io.snoopy.engine.remediation;

public record RemediationStep(
        int stepNumber,
        String category, // CONTAIN, ASSESS, CLEANUP, PREVENT
        String title,
        String description,
        String commandOrUrl
) {}
