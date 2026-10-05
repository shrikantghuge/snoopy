package io.snoopy.app.api;

import io.snoopy.app.db.entity.FindingEntity;
import io.snoopy.app.db.repository.FindingRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/v1/reports")
public class ReportController {

    private final FindingRepository repository;
    private final String defaultTenantId;

    public ReportController(
            FindingRepository repository,
            @Value("${snoopy.tenant.default-id:tenant-default}") String defaultTenantId
    ) {
        this.repository = repository;
        this.defaultTenantId = defaultTenantId;
    }

    @GetMapping("/findings.json")
    public List<FindingEntity> exportJson() {
        return repository.findByTenantId(defaultTenantId);
    }

    @GetMapping(value = "/findings.csv", produces = "text/csv")
    public ResponseEntity<String> exportCsv() {
        List<FindingEntity> findings = repository.findByTenantId(defaultTenantId);
        StringBuilder sb = new StringBuilder();
        sb.append("FindingID,SecretType,Provider,MaskedSecret,Severity,Status,ExposureState,RiskScore,FirstSeenAt,LastSeenAt,OccurrencesCount\n");

        for (FindingEntity f : findings) {
            sb.append(String.join(",",
                    f.getId(),
                    f.getSecretType(),
                    f.getProvider(),
                    "\"" + f.getMaskedSecret() + "\"",
                    f.getSeverity(),
                    f.getStatus(),
                    f.getExposureState(),
                    String.valueOf(f.getRiskScore()),
                    String.valueOf(f.getFirstSeenAt()),
                    String.valueOf(f.getLastSeenAt()),
                    String.valueOf(f.getOccurrences() != null ? f.getOccurrences().size() : 0)
            )).append("\n");
        }

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=findings.csv")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(sb.toString());
    }

    @GetMapping(value = "/findings.sarif", produces = "application/json")
    public ResponseEntity<?> exportSarif() {
        List<FindingEntity> findings = repository.findByTenantId(defaultTenantId);

        Map<String, Object> sarif = new LinkedHashMap<>();
        sarif.put("$schema", "https://raw.githubusercontent.com/oasis-tcs/sarif-spec/master/Schemata/sarif-schema-2.1.0.json");
        sarif.put("version", "2.1.0");

        List<Map<String, Object>> runs = new ArrayList<>();
        Map<String, Object> run = new LinkedHashMap<>();

        Map<String, Object> tool = Map.of("driver", Map.of(
                "name", "Snoopy Credential Exposure Discovery Platform",
                "version", "0.1.0",
                "informationUri", "https://github.com/shrikantghuge/snoopy"
        ));
        run.put("tool", tool);

        List<Map<String, Object>> results = new ArrayList<>();
        for (FindingEntity f : findings) {
            Map<String, Object> res = new LinkedHashMap<>();
            res.put("ruleId", f.getRuleId() != null ? f.getRuleId() : f.getSecretType());
            res.put("message", Map.of("text", "Exposed credential detected: " + f.getSecretType() + " (" + f.getMaskedSecret() + ")"));
            res.put("level", "CRITICAL".equals(f.getSeverity()) || "HIGH".equals(f.getSeverity()) ? "error" : "warning");

            results.add(res);
        }
        run.put("results", results);
        runs.add(run);
        sarif.put("runs", runs);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=findings.sarif.json")
                .body(sarif);
    }
}
