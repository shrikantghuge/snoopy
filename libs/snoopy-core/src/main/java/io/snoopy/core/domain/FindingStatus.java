package io.snoopy.core.domain;

public enum FindingStatus {
    OPEN,
    ACKNOWLEDGED,
    IN_REMEDIATION,
    RESOLVED,
    FALSE_POSITIVE,
    ACCEPTED_RISK,
    REOPENED;

    public boolean isClosed() {
        return this == RESOLVED || this == FALSE_POSITIVE || this == ACCEPTED_RISK;
    }
}
