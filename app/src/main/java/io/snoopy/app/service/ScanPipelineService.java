package io.snoopy.app.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.snoopy.app.db.entity.FindingEntity;
import io.snoopy.app.db.entity.FindingOccurrenceEntity;
import io.snoopy.app.db.entity.SecretFingerprintEntity;
import io.snoopy.app.db.repository.FindingOccurrenceRepository;
import io.snoopy.app.db.repository.FindingRepository;
import io.snoopy.app.db.repository.SecretFingerprintRepository;
import io.snoopy.core.domain.Actor;
import io.snoopy.core.domain.ExposureState;
import io.snoopy.core.domain.FindingStatus;
import io.snoopy.core.domain.VerificationStatus;
import io.snoopy.core.security.Fingerprinter;
import io.snoopy.core.security.SecretMasker;
import io.snoopy.core.spi.connector.ContentPayload;
import io.snoopy.core.spi.connector.ContentSink;
import io.snoopy.engine.content.ContentExtractorService;
import io.snoopy.engine.correlation.CorrelationEngine;
import io.snoopy.engine.correlation.OccurrenceRecord;
import io.snoopy.engine.detection.detectors.BuiltInDetectors;
import io.snoopy.engine.detection.filter.FalsePositiveFilter;
import io.snoopy.engine.detection.spi.SecretCandidate;
import io.snoopy.engine.detection.spi.SecretDetector;
import io.snoopy.engine.risk.RiskEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;

@Service
public class ScanPipelineService {

    private static final Logger log = LoggerFactory.getLogger(ScanPipelineService.class);
    private final ObjectMapper mapper = new ObjectMapper();

    private final ContentExtractorService extractorService = new ContentExtractorService();
    private final List<SecretDetector> detectors = BuiltInDetectors.createAll();

    private final SecretFingerprintRepository fingerprintRepository;
    private final FindingRepository findingRepository;
    private final FindingOccurrenceRepository occurrenceRepository;

    private final String defaultTenantId;
    private final Fingerprinter fingerprinter;

    public ScanPipelineService(
            SecretFingerprintRepository fingerprintRepository,
            FindingRepository findingRepository,
            FindingOccurrenceRepository occurrenceRepository,
            @Value("${snoopy.tenant.default-id:tenant-default}") String defaultTenantId,
            @Value("${snoopy.tenant.default-key:01234567890123456789012345678901}") String defaultTenantKey
    ) {
        this.fingerprintRepository = fingerprintRepository;
        this.findingRepository = findingRepository;
        this.occurrenceRepository = occurrenceRepository;
        this.defaultTenantId = defaultTenantId;
        this.fingerprinter = new Fingerprinter(defaultTenantKey.getBytes(StandardCharsets.UTF_8));
    }

    public ContentSink createSink(String tenantId, String jobId) {
        String effectiveTenant = tenantId != null ? tenantId : defaultTenantId;
        return new PipelineSink(effectiveTenant, jobId);
    }

    private class PipelineSink implements ContentSink {
        private final String tenantId;
        private final String jobId;

        public PipelineSink(String tenantId, String jobId) {
            this.tenantId = tenantId;
            this.jobId = jobId;
        }

        @Override
        public void accept(ContentPayload payload) {
            String text = extractorService.extractText(payload.content(), payload.mimeType(), payload.filename());
            log.info("PipelineSink.accept processing payload {}: text len {}", payload.title(), text != null ? text.length() : 0);
            if (text == null || text.isBlank()) return;

            for (SecretDetector detector : detectors) {
                List<SecretCandidate> candidates = detector.detect(payload, text);
                if (!candidates.isEmpty()) {
                    log.info("Detector {} found {} candidates in {}", detector.getMetadata().id(), candidates.size(), payload.title());
                }
                for (SecretCandidate candidate : candidates) {
                    if (FalsePositiveFilter.isFalsePositive(candidate)) {
                        log.info("Candidate suppressed as false positive: {}", candidate.matchedValue());
                        continue; // Suppress false positives
                    }
                    processCandidate(tenantId, payload, candidate);
                }
            }
        }

        @Override
        public void checkpoint(String scopeKey, String cursor) {
            log.info("Checkpoint reached for scope {} with cursor {}", scopeKey, cursor);
        }

        @Override
        public void failure(String scopeKey, String objectRef, String reason, Throwable error) {
            log.warn("Scan failure on scope {} object {}: {} - {}", scopeKey, objectRef, reason, error != null ? error.getMessage() : "");
        }
    }

    @Transactional
    public void processCandidate(String tenantId, ContentPayload payload, SecretCandidate candidate) {
        String credFingerprint = fingerprinter.credentialFingerprint(candidate.matchedValue());
        String occFingerprint = fingerprinter.occurrenceFingerprint(
                credFingerprint,
                payload.sourceType().name(),
                payload.externalObjectId(),
                payload.version(),
                String.valueOf(candidate.lineNumber())
        );

        // 1. Get or create Fingerprint entity
        SecretFingerprintEntity fpEntity = fingerprintRepository
                .findByTenantIdAndFingerprint(tenantId, credFingerprint)
                .orElseGet(() -> {
                    SecretFingerprintEntity entity = new SecretFingerprintEntity();
                    entity.setId(UUID.randomUUID().toString());
                    entity.setTenantId(tenantId);
                    entity.setFingerprint(credFingerprint);
                    entity.setSecretType(candidate.secretType());
                    entity.setProvider(candidate.provider());
                    return fingerprintRepository.saveAndFlush(entity);
                });

        // 2. Get or create Finding entity
        FindingEntity finding = findingRepository
                .findByTenantIdAndFingerprintId(tenantId, fpEntity.getId())
                .orElseGet(() -> {
                    FindingEntity f = new FindingEntity();
                    f.setId("f-" + UUID.randomUUID().toString().substring(0, 8));
                    f.setTenantId(tenantId);
                    f.setFingerprint(fpEntity);
                    f.setRuleId(candidate.detectorId());
                    f.setSourceType(payload.sourceType().name());
                    f.setSecretType(candidate.secretType());
                    f.setProvider(candidate.provider());
                    f.setMaskedSecret(SecretMasker.mask(candidate.matchedValue()));
                    f.setStatus(FindingStatus.OPEN.name());
                    f.setSeverity(candidate.severity().name());
                    f.setConfidence(candidate.confidence());
                    f.setVerificationStatus(VerificationStatus.NOT_ATTEMPTED.name());
                    f.setFirstSeenAt(payload.timestamp() != null ? payload.timestamp() : Instant.now());
                    f.setLastSeenAt(payload.timestamp() != null ? payload.timestamp() : Instant.now());
                    return f;
                });

        // 3. Create or update occurrence
        FindingOccurrenceEntity occurrence = new FindingOccurrenceEntity();
        occurrence.setId(UUID.randomUUID().toString());
        occurrence.setFinding(finding);
        occurrence.setOccurrenceFingerprint(occFingerprint);
        occurrence.setSourceType(payload.sourceType().name());
        occurrence.setScopeKey(payload.scopeKey());
        occurrence.setExternalObjectId(payload.externalObjectId());
        occurrence.setSeriesId(payload.seriesId());
        occurrence.setVersion(payload.version());
        occurrence.setCurrent(payload.current());
        occurrence.setLineNumber(candidate.lineNumber());
        occurrence.setTimestamp(payload.timestamp() != null ? payload.timestamp() : Instant.now());
        occurrence.setSourceUrl(payload.sourceUrl());
        occurrence.setContextSnippet(candidate.contextSnippet());

        try {
            if (payload.author() != null) {
                occurrence.setAuthorJson(mapper.writeValueAsString(payload.author()));
            }
        } catch (Exception ignored) {}

        finding.getOccurrences().add(occurrence);

        // 4. Update timestamps, exposure state, risk score & introducer
        Instant ts = payload.timestamp() != null ? payload.timestamp() : Instant.now();
        if (ts.isBefore(finding.getFirstSeenAt())) finding.setFirstSeenAt(ts);
        if (ts.isAfter(finding.getLastSeenAt())) finding.setLastSeenAt(ts);

        // Recompute exposure state across occurrences
        List<OccurrenceRecord> records = finding.getOccurrences().stream().map(o -> {
            Actor a = null;
            try {
                if (o.getAuthorJson() != null) a = mapper.readValue(o.getAuthorJson(), Actor.class);
            } catch (Exception ignored) {}
            return new OccurrenceRecord(
                    o.getOccurrenceFingerprint(), credFingerprint, o.getSourceType(),
                    o.getScopeKey(), o.getExternalObjectId(), o.getSeriesId(), o.getVersion(),
                    o.isCurrent(), o.getLineNumber(), finding.getMaskedSecret(), a, o.getTimestamp(),
                    o.getSourceUrl(), o.getContextSnippet()
            );
        }).toList();

        ExposureState state = CorrelationEngine.calculateExposureState(records);
        finding.setExposureState(state.name());

        // Reopen resolved finding if credential reappears in new scan
        if (state == ExposureState.CURRENT && FindingStatus.RESOLVED.name().equals(finding.getStatus())) {
            finding.setStatus(FindingStatus.REOPENED.name());
        }

        Actor introducer = CorrelationEngine.findIntroducer(records);
        try {
            if (introducer != null) {
                finding.setIntroducedByJson(mapper.writeValueAsString(introducer));
                finding.setLikelyOwnerJson(mapper.writeValueAsString(introducer)); // Default likely owner to introducer
            }
        } catch (Exception ignored) {}

        int risk = RiskEngine.calculateRiskScore(
                candidate.severity(),
                candidate.confidence(),
                state,
                VerificationStatus.valueOf(finding.getVerificationStatus()),
                finding.getOccurrences().size(),
                payload.scopeKey().toLowerCase().contains("prod")
        );
        finding.setRiskScore(risk);

        findingRepository.saveAndFlush(finding);
        log.info("Saved finding {} with masked secret {}", finding.getId(), finding.getMaskedSecret());
    }
}
