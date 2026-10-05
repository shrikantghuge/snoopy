package io.snoopy.engine.correlation;

import io.snoopy.core.domain.Actor;
import io.snoopy.core.domain.ExposureState;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;

public final class CorrelationEngine {

    private CorrelationEngine() {}

    /**
     * Calculates the ExposureState (spec §2.3, §23) given a list of occurrences for a credential.
     */
    public static ExposureState calculateExposureState(List<OccurrenceRecord> occurrences) {
        if (occurrences == null || occurrences.isEmpty()) {
            return ExposureState.HISTORICAL;
        }

        boolean hasCurrent = occurrences.stream().anyMatch(OccurrenceRecord::isCurrent);
        boolean hasHistorical = occurrences.stream().anyMatch(o -> !o.isCurrent());

        if (hasCurrent && hasHistorical) {
            return ExposureState.CURRENT_AND_HISTORICAL;
        } else if (hasCurrent) {
            return ExposureState.CURRENT;
        } else {
            return ExposureState.HISTORICAL;
        }
    }

    /**
     * Determines who introduced the credential (first occurrence by timestamp).
     */
    public static Actor findIntroducer(List<OccurrenceRecord> occurrences) {
        if (occurrences == null || occurrences.isEmpty()) return null;
        return occurrences.stream()
                .filter(o -> o.author() != null)
                .min(Comparator.comparing(o -> o.timestamp() != null ? o.timestamp() : Instant.EPOCH))
                .map(OccurrenceRecord::author)
                .orElse(null);
    }
}
