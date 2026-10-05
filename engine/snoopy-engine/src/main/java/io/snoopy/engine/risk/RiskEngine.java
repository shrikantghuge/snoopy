package io.snoopy.engine.risk;

import io.snoopy.core.domain.ExposureState;
import io.snoopy.core.domain.Severity;
import io.snoopy.core.domain.VerificationStatus;

public final class RiskEngine {

    private RiskEngine() {}

    public static int calculateRiskScore(
            Severity severity,
            int confidence,
            ExposureState exposureState,
            VerificationStatus verificationStatus,
            int occurrenceCount,
            boolean isProduction
    ) {
        double score = confidence * 0.4;

        if (severity == Severity.CRITICAL) score += 35;
        else if (severity == Severity.HIGH) score += 25;
        else if (severity == Severity.MEDIUM) score += 15;
        else score += 5;

        if (exposureState == ExposureState.CURRENT || exposureState == ExposureState.CURRENT_AND_HISTORICAL) {
            score += 15;
        }

        if (verificationStatus == VerificationStatus.ACTIVE) {
            score += 10;
        }

        if (isProduction) {
            score += 10;
        }

        if (occurrenceCount > 5) {
            score += 5;
        }

        return Math.min(100, (int) Math.round(score));
    }
}
