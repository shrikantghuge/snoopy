package io.snoopy.app;

import io.snoopy.app.db.entity.FindingEntity;
import io.snoopy.app.db.entity.SourceConnectionEntity;
import io.snoopy.app.db.entity.TenantEntity;
import io.snoopy.app.db.repository.FindingRepository;
import io.snoopy.app.db.repository.SourceConnectionRepository;
import io.snoopy.app.db.repository.TenantRepository;
import io.snoopy.app.service.ScanOrchestratorService;
import io.snoopy.core.spi.connector.ScanMode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.File;
import java.nio.file.Files;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class FilesystemScanIntegrationTest {

    @Autowired
    private ScanOrchestratorService orchestratorService;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private SourceConnectionRepository connectionRepository;

    @Autowired
    private FindingRepository findingRepository;

    @Test
    void scanFilesystemFindsExposedCredentials(@TempDir File tempDir) throws Exception {
        String tenantId = "tenant-fs";
        tenantRepository.save(new TenantEntity(tenantId, "FS Tenant", "ACTIVE"));

        // Plant synthetic secrets
        File configFile = new File(tempDir, "config.yml");
        String content = """
                aws_access_key: AKIAIOSFODNN7EXAMPLE
                github_token: ghp_123456789012345678901234567890123456
                dummy_key: <YOUR_API_KEY>
                """;
        Files.writeString(configFile.toPath(), content);

        // Create connection
        SourceConnectionEntity connection = new SourceConnectionEntity();
        connection.setId("src-fs-test");
        connection.setTenantId(tenantId);
        connection.setType("FILESYSTEM");
        connection.setDisplayName("Test Filesystem");
        connection.setSettingsJson("{\"directoryPath\":\"" + tempDir.getAbsolutePath().replace("\\", "\\\\") + "\"}");
        connection.setStatus("ACTIVE");
        connectionRepository.save(connection);

        // Run sync scan
        orchestratorService.startScanSync(connection.getId(), ScanMode.FULL);

        List<FindingEntity> findings = findingRepository.findByTenantId(tenantId);
        assertThat(findings).isNotEmpty();

        // Check that secrets were discovered and masked
        boolean foundAws = findings.stream().anyMatch(f -> "AWS_ACCESS_KEY".equals(f.getSecretType()) && "AKIA••••••••MPLE".equals(f.getMaskedSecret()));
        boolean foundGithub = findings.stream().anyMatch(f -> "GITHUB_TOKEN".equals(f.getSecretType()));
        boolean falsePositiveSuppressed = findings.stream().noneMatch(f -> f.getMaskedSecret().contains("<YOUR_API_KEY>"));

        assertThat(foundAws).isTrue();
        assertThat(foundGithub).isTrue();
        assertThat(falsePositiveSuppressed).isTrue();
    }
}
