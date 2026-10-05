package io.snoopy.engine.correlation;

import io.snoopy.core.domain.Actor;
import io.snoopy.core.domain.ExposureState;
import io.snoopy.core.domain.FindingStatus;
import io.snoopy.core.domain.Severity;
import io.snoopy.core.domain.VerificationStatus;

import java.time.Instant;
import java.util.List;

public record FindingRecord(
        String id,
        String tenantId,
        String credentialFingerprint,
        String secretType,
        String provider,
        String maskedSecret,
        Severity severity,
        int confidence,
        int riskScore,
        FindingStatus status,
        ExposureState exposureState,
        VerificationStatus verificationStatus,
        Instant firstSeenAt,
        Instant lastSeenAt,
        Actor introducedBy,
        Actor likelyOwner,
        List<OccurrenceRecord> occurrences
) {
    public FindingRecord {
        occurrences = occurrences == null ? List.of() : List.copyOf(occurrences);
    }
}
