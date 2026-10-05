package io.snoopy.app.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.snoopy.app.db.entity.SourceConnectionEntity;
import io.snoopy.app.db.entity.SourceScopeEntity;
import io.snoopy.app.db.repository.SourceConnectionRepository;
import io.snoopy.app.service.ScanOrchestratorService;
import io.snoopy.connector.confluence.ConfluenceConnector;
import io.snoopy.connector.filesystem.FilesystemConnector;
import io.snoopy.connector.github.GitHubConnector;
import io.snoopy.connector.jira.JiraConnector;
import io.snoopy.core.domain.SourceType;
import io.snoopy.core.spi.connector.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.*;

@RestController
@RequestMapping("/api/v1/sources")
public class SourceController {

    private final ObjectMapper mapper = new ObjectMapper();
    private final SourceConnectionRepository repository;
    private final ScanOrchestratorService orchestratorService;
    private final String defaultTenantId;

    private final Map<SourceType, SourceConnector> connectorMap = new EnumMap<>(SourceType.class);

    public SourceController(
            SourceConnectionRepository repository,
            ScanOrchestratorService orchestratorService,
            @Value("${snoopy.tenant.default-id:tenant-default}") String defaultTenantId
    ) {
        this.repository = repository;
        this.orchestratorService = orchestratorService;
        this.defaultTenantId = defaultTenantId;

        connectorMap.put(SourceType.FILESYSTEM, new FilesystemConnector());
        connectorMap.put(SourceType.GITHUB, new GitHubConnector());
        connectorMap.put(SourceType.JIRA_CLOUD, new JiraConnector());
        connectorMap.put(SourceType.CONFLUENCE_CLOUD, new ConfluenceConnector());
    }

    @GetMapping
    public List<SourceConnectionEntity> listSources() {
        return repository.findByTenantId(defaultTenantId);
    }

    @PostMapping
    public SourceConnectionEntity createSource(@RequestBody Map<String, Object> body) throws Exception {
        String typeStr = (String) body.get("type");
        String name = (String) body.get("displayName");
        String baseUrl = (String) body.getOrDefault("baseUrl", "");
        String authType = (String) body.getOrDefault("authType", "TOKEN");

        Map<String, String> creds = (Map<String, String>) body.getOrDefault("credentials", Map.of());
        Map<String, String> settings = (Map<String, String>) body.getOrDefault("settings", Map.of());

        SourceConnectionEntity entity = new SourceConnectionEntity();
        entity.setId("src-" + UUID.randomUUID().toString().substring(0, 8));
        entity.setTenantId(defaultTenantId);
        entity.setType(typeStr);
        entity.setDisplayName(name);
        entity.setBaseUrl(baseUrl);
        entity.setAuthType(authType);
        entity.setCredentialsJson(mapper.writeValueAsString(creds));
        entity.setSettingsJson(mapper.writeValueAsString(settings));
        entity.setStatus("ACTIVE");

        // Optional scope selection
        List<Map<String, Object>> scopesList = (List<Map<String, Object>>) body.get("scopes");
        if (scopesList != null) {
            for (Map<String, Object> scopeItem : scopesList) {
                SourceScopeEntity scope = new SourceScopeEntity();
                scope.setId(UUID.randomUUID().toString());
                scope.setSourceConnection(entity);
                scope.setExternalId((String) scopeItem.get("externalId"));
                scope.setScopeType((String) scopeItem.getOrDefault("scopeType", "REPOSITORY"));
                scope.setScanHistory(Boolean.parseBoolean(scopeItem.getOrDefault("scanHistory", "true").toString()));
                scope.setScanComments(Boolean.parseBoolean(scopeItem.getOrDefault("scanComments", "true").toString()));
                scope.setScanAttachments(Boolean.parseBoolean(scopeItem.getOrDefault("scanAttachments", "true").toString()));
                entity.getScopes().add(scope);
            }
        }

        return repository.save(entity);
    }

    @PostMapping("/{id}/test")
    public ConnectionHealth testConnection(@PathVariable String id) throws Exception {
        SourceConnectionEntity connection = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Connection not found: " + id));

        SourceType sourceType = SourceType.valueOf(connection.getType());
        SourceConnector connector = connectorMap.get(sourceType);

        Map<String, String> creds = connection.getCredentialsJson() != null ? mapper.readValue(connection.getCredentialsJson(), Map.class) : Map.of();
        Map<String, String> settings = connection.getSettingsJson() != null ? mapper.readValue(connection.getSettingsJson(), Map.class) : Map.of();

        ConnectionConfig config = new ConnectionConfig(
                connection.getId(), sourceType, connection.getBaseUrl(),
                connection.getAuthType(), creds, settings
        );

        ConnectionHealth health = connector.checkConnection(config);
        connection.setLastHealthCheck(Instant.now());
        connection.setStatus(health.ok() ? "HEALTHY" : "UNHEALTHY");
        repository.save(connection);

        return health;
    }

    @PostMapping("/{id}/scan")
    public ResponseEntity<?> triggerScan(@PathVariable String id, @RequestParam(defaultValue = "FULL") String mode) {
        ScanMode scanMode = ScanMode.valueOf(mode.toUpperCase());
        String jobId = orchestratorService.startScan(id, scanMode);
        return ResponseEntity.ok(Map.of(
                "jobId", jobId,
                "status", "RUNNING",
                "message", "Scan job started asynchronously"
        ));
    }
}
