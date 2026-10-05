package io.snoopy.app.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.snoopy.app.db.entity.FindingEntity;
import io.snoopy.app.db.entity.FindingOccurrenceEntity;
import io.snoopy.app.db.repository.FindingRepository;
import io.snoopy.core.domain.Actor;
import io.snoopy.core.domain.SourceType;
import io.snoopy.engine.remediation.RemediationPlaybook;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.*;

@RestController
@RequestMapping("/api/v1/findings")
public class FindingController {

    private final ObjectMapper mapper = new ObjectMapper();
    private final FindingRepository repository;
    private final String defaultTenantId;

    public FindingController(
            FindingRepository repository,
            @Value("${snoopy.tenant.default-id:tenant-default}") String defaultTenantId
    ) {
        this.repository = repository;
        this.defaultTenantId = defaultTenantId;
    }

    @GetMapping
    public List<Map<String, Object>> listFindings(
            @RequestParam(required = false) String severity,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String exposureState
    ) {
        List<FindingEntity> entities = repository.findByTenantId(defaultTenantId);
        List<Map<String, Object>> result = new ArrayList<>();

        for (FindingEntity f : entities) {
            if (severity != null && !severity.equalsIgnoreCase(f.getSeverity())) continue;
            if (status != null && !status.equalsIgnoreCase(f.getStatus())) continue;
            if (exposureState != null && !exposureState.equalsIgnoreCase(f.getExposureState())) continue;

            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", f.getId());
            map.put("secretType", f.getSecretType());
            map.put("provider", f.getProvider());
            map.put("maskedSecret", f.getMaskedSecret());
            map.put("severity", f.getSeverity());
            map.put("confidence", f.getConfidence());
            map.put("riskScore", f.getRiskScore());
            map.put("status", f.getStatus());
            map.put("exposureState", f.getExposureState());
            map.put("verificationStatus", f.getVerificationStatus());
            map.put("firstSeenAt", f.getFirstSeenAt());
            map.put("lastSeenAt", f.getLastSeenAt());
            map.put("occurrenceCount", f.getOccurrences() != null ? f.getOccurrences().size() : 0);
            map.put("introducedBy", parseActor(f.getIntroducedByJson()));
            map.put("likelyOwner", parseActor(f.getLikelyOwnerJson()));

            result.add(map);
        }

        return result;
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getFindingDetails(@PathVariable String id) {
        FindingEntity f = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Finding not found: " + id));

        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", f.getId());
        map.put("secretType", f.getSecretType());
        map.put("provider", f.getProvider());
        map.put("maskedSecret", f.getMaskedSecret());
        map.put("severity", f.getSeverity());
        map.put("confidence", f.getConfidence());
        map.put("riskScore", f.getRiskScore());
        map.put("status", f.getStatus());
        map.put("exposureState", f.getExposureState());
        map.put("verificationStatus", f.getVerificationStatus());
        map.put("firstSeenAt", f.getFirstSeenAt());
        map.put("lastSeenAt", f.getLastSeenAt());
        map.put("introducedBy", parseActor(f.getIntroducedByJson()));
        map.put("likelyOwner", parseActor(f.getLikelyOwnerJson()));

        // Timeline of occurrences
        List<Map<String, Object>> occurrencesList = new ArrayList<>();
        if (f.getOccurrences() != null) {
            for (FindingOccurrenceEntity o : f.getOccurrences()) {
                Map<String, Object> occMap = new LinkedHashMap<>();
                occMap.put("id", o.getId());
                occMap.put("sourceType", o.getSourceType());
                occMap.put("scopeKey", o.getScopeKey());
                occMap.put("externalObjectId", o.getExternalObjectId());
                occMap.put("seriesId", o.getSeriesId());
                occMap.put("version", o.getVersion());
                occMap.put("isCurrent", o.isCurrent());
                occMap.put("lineNumber", o.getLineNumber());
                occMap.put("timestamp", o.getTimestamp());
                occMap.put("sourceUrl", o.getSourceUrl());
                occMap.put("contextSnippet", o.getContextSnippet());
                occMap.put("author", parseActor(o.getAuthorJson()));

                occurrencesList.add(occMap);
            }
        }
        map.put("occurrences", occurrencesList);

        return ResponseEntity.ok(map);
    }

    @GetMapping("/{id}/remediation")
    public ResponseEntity<RemediationPlaybook> getRemediationPlaybook(@PathVariable String id) {
        FindingEntity f = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Finding not found: " + id));

        SourceType st = SourceType.valueOf(f.getSourceType());
        String url = f.getOccurrences() != null && !f.getOccurrences().isEmpty() ? f.getOccurrences().get(0).getSourceUrl() : null;

        RemediationPlaybook playbook = RemediationPlaybook.generate(f.getSecretType(), st, url);
        return ResponseEntity.ok(playbook);
    }

    @PatchMapping("/{id}")
    public ResponseEntity<?> updateStatus(@PathVariable String id, @RequestBody Map<String, String> body) {
        FindingEntity f = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Finding not found: " + id));

        String newStatus = body.get("status");
        if (newStatus != null) {
            f.setStatus(newStatus.toUpperCase());
            f.setUpdatedAt(Instant.now());
            repository.save(f);
        }

        return ResponseEntity.ok(Map.of("id", f.getId(), "status", f.getStatus()));
    }

    private Actor parseActor(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            return mapper.readValue(json, Actor.class);
        } catch (Exception e) {
            return null;
        }
    }
}
