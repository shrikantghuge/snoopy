package io.snoopy.core.domain;

public enum Severity {
    CRITICAL, HIGH, MEDIUM, LOW, INFO;

    /** Classification from spec section 19: 90-100 CRITICAL, 75-89 HIGH, 50-74 MEDIUM, 25-49 LOW, else INFO. */
    public static Severity fromScore(int score) {
        if (score >= 90) return CRITICAL;
        if (score >= 75) return HIGH;
        if (score >= 50) return MEDIUM;
        if (score >= 25) return LOW;
        return INFO;
    }

    public int rank() {
        return values().length - ordinal();
    }
}
