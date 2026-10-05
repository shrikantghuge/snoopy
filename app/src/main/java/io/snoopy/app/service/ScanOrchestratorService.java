package io.snoopy.app.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.snoopy.app.db.entity.ScanJobEntity;
import io.snoopy.app.db.entity.SourceConnectionEntity;
import io.snoopy.app.db.entity.SourceScopeEntity;
import io.snoopy.app.db.repository.ScanJobRepository;
import io.snoopy.app.db.repository.SourceConnectionRepository;
import io.snoopy.connector.confluence.ConfluenceConnector;
import io.snoopy.connector.filesystem.FilesystemConnector;
import io.snoopy.connector.github.GitHubConnector;
import io.snoopy.connector.jira.JiraConnector;
import io.snoopy.core.domain.SourceType;
import io.snoopy.core.spi.connector.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.File;
import java.time.Instant;
import java.util.*;

@Service
public class ScanOrchestratorService {

    private static final Logger log = LoggerFactory.getLogger(ScanOrchestratorService.class);
    private final ObjectMapper mapper = new ObjectMapper();

    private final Map<SourceType, SourceConnector> connectorMap = new EnumMap<>(SourceType.class);

    private final SourceConnectionRepository connectionRepository;
    private final ScanJobRepository scanJobRepository;
    private final ScanPipelineService pipelineService;

    public ScanOrchestratorService(
            SourceConnectionRepository connectionRepository,
            ScanJobRepository scanJobRepository,
            ScanPipelineService pipelineService
    ) {
        this.connectionRepository = connectionRepository;
        this.scanJobRepository = scanJobRepository;
        this.pipelineService = pipelineService;

        // Register default connectors
        connectorMap.put(SourceType.FILESYSTEM, new FilesystemConnector());
        connectorMap.put(SourceType.GITHUB, new GitHubConnector());
        connectorMap.put(SourceType.JIRA_CLOUD, new JiraConnector());
        connectorMap.put(SourceType.CONFLUENCE_CLOUD, new ConfluenceConnector());
    }

    public String startScan(String connectionId, ScanMode mode) {
        SourceConnectionEntity connection = connectionRepository.findById(connectionId)
                .orElseThrow(() -> new IllegalArgumentException("Source connection not found: " + connectionId));

        ScanJobEntity job = new ScanJobEntity();
        job.setId("job-" + UUID.randomUUID().toString().substring(0, 8));
        job.setTenantId(connection.getTenantId());
        job.setSourceConnectionId(connectionId);
        job.setScanType(mode.name());
        job.setStatus("RUNNING");
        job.setStartedAt(Instant.now());

        scanJobRepository.save(job);

        // Execute scan
        executeScanAsync(connection, job, mode);

        return job.getId();
    }

    public void startScanSync(String connectionId, ScanMode mode) {
        SourceConnectionEntity connection = connectionRepository.findById(connectionId)
                .orElseThrow(() -> new IllegalArgumentException("Source connection not found: " + connectionId));

        ScanJobEntity job = new ScanJobEntity();
        job.setId("job-" + UUID.randomUUID().toString().substring(0, 8));
        job.setTenantId(connection.getTenantId());
        job.setSourceConnectionId(connectionId);
        job.setScanType(mode.name());
        job.setStatus("RUNNING");
        job.setStartedAt(Instant.now());

        scanJobRepository.save(job);

        executeScanInternal(connection, job, mode);
    }

    @Async
    public void executeScanAsync(SourceConnectionEntity connection, ScanJobEntity job, ScanMode mode) {
        executeScanInternal(connection, job, mode);
    }

    private void executeScanInternal(SourceConnectionEntity connection, ScanJobEntity job, ScanMode mode) {
        SourceType sourceType = SourceType.valueOf(connection.getType());
        SourceConnector connector = connectorMap.get(sourceType);

        if (connector == null) {
            job.setStatus("FAILED");
            job.setErrorMessage("Unsupported connector: " + sourceType);
            job.setCompletedAt(Instant.now());
            scanJobRepository.save(job);
            return;
        }

        try {
            Map<String, String> creds = parseMap(connection.getCredentialsJson());
            Map<String, String> settings = parseMap(connection.getSettingsJson());

            ConnectionConfig config = new ConnectionConfig(
                    connection.getId(), sourceType, connection.getBaseUrl(),
                    connection.getAuthType(), creds, settings
            );

            List<ScopeSelection> scopes = new ArrayList<>();
            if (connection.getScopes() != null && !connection.getScopes().isEmpty()) {
                for (SourceScopeEntity scopeEntity : connection.getScopes()) {
                    scopes.add(new ScopeSelection(
                            scopeEntity.getExternalId(),
                            scopeEntity.getScopeType(),
                            scopeEntity.getIncludePattern(),
                            scopeEntity.getExcludePattern(),
                            scopeEntity.isScanHistory(),
                            scopeEntity.isScanComments(),
                            scopeEntity.isScanAttachments()
                    ));
                }
            } else {
                // Discover default scope if none configured
                List<ScopeItem> discovered = connector.discoverScopes(config);
                for (ScopeItem item : discovered) {
                    scopes.add(ScopeSelection.all(item.externalId(), item.scopeType()));
                }
            }

            File tempWorkDir = new File(System.getProperty("java.io.tmpdir"), "snoopy-scan-" + job.getId());
            tempWorkDir.mkdirs();

            ScanContext context = new ScanContext(
                    config, scopes, mode, Map.of(), ScanOptions.defaults(), tempWorkDir.toPath()
            );

            ContentSink sink = pipelineService.createSink(connection.getTenantId(), job.getId());

            log.info("Starting {} scan for connection {}", mode, connection.getDisplayName());
            connector.scan(context, sink);

            job.setStatus("COMPLETED");
            job.setCompletedAt(Instant.now());

        } catch (Exception e) {
            log.error("Scan failed for job {}", job.getId(), e);
            job.setStatus("FAILED");
            job.setErrorMessage(e.getMessage());
            job.setCompletedAt(Instant.now());
        } finally {
            scanJobRepository.save(job);
        }
    }

    private Map<String, String> parseMap(String json) {
        if (json == null || json.isBlank()) return Map.of();
        try {
            return mapper.readValue(json, new TypeReference<Map<String, String>>() {});
        } catch (Exception e) {
            return Map.of();
        }
    }
}
