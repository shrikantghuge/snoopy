package io.snoopy.core.domain;

public enum VerificationStatus {
    NOT_ATTEMPTED,
    ACTIVE,
    INVALID,
    REVOKED,
    EXPIRED,
    RATE_LIMITED,
    UNKNOWN,
    NOT_SUPPORTED
}
