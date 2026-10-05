package io.snoopy.app;

import io.snoopy.app.db.entity.FindingEntity;
import io.snoopy.app.db.entity.SourceConnectionEntity;
import io.snoopy.app.db.entity.SourceScopeEntity;
import io.snoopy.app.db.entity.TenantEntity;
import io.snoopy.app.db.repository.FindingRepository;
import io.snoopy.app.db.repository.SourceConnectionRepository;
import io.snoopy.app.db.repository.TenantRepository;
import io.snoopy.app.service.ScanOrchestratorService;
import io.snoopy.core.spi.connector.ScanMode;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.PersonIdent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.File;
import java.nio.file.Files;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class GitHistoryCorrelationTest {

    @Autowired
    private ScanOrchestratorService orchestratorService;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private SourceConnectionRepository connectionRepository;

    @Autowired
    private FindingRepository findingRepository;

    @Test
    void gitHistoryTracksIntroducedAuthorAndReappearedState(@TempDir File repoDir) throws Exception {
        String tenantId = "tenant-git";
        tenantRepository.save(new TenantEntity(tenantId, "Git Tenant", "ACTIVE"));

        // Distinct timestamps so Commit A is strictly the oldest
        PersonIdent devAlice = new PersonIdent("Alice Developer", "alice@enterprise.com", 1600000000L * 1000L, 0);
        PersonIdent devBob = new PersonIdent("Bob Security", "bob@enterprise.com", 1650000000L * 1000L, 0);
        PersonIdent devAliceRecent = new PersonIdent("Alice Developer", "alice@enterprise.com", 1700000000L * 1000L, 0);

        try (Git git = Git.init().setDirectory(repoDir).call()) {
            // Commit A (by Alice): Introduces secret in AWS_KEY.txt
            File fileA = new File(repoDir, "AWS_KEY.txt");
            Files.writeString(fileA.toPath(), "aws_access_key=AKIAIOSFODNN7EXAMPLE\n");
            git.add().addFilepattern("AWS_KEY.txt").call();
            git.commit().setAuthor(devAlice).setCommitter(devAlice).setMessage("Add AWS credentials").call();

            // Commit B (by Bob): Deletes AWS_KEY.txt
            git.rm().addFilepattern("AWS_KEY.txt").call();
            git.commit().setAuthor(devBob).setCommitter(devBob).setMessage("Remove AWS credentials").call();

            // Commit C (by Alice): Re-introduces secret in src/config.properties
            File srcDir = new File(repoDir, "src");
            srcDir.mkdirs();
            File fileC = new File(srcDir, "config.properties");
            Files.writeString(fileC.toPath(), "aws_access_key=AKIAIOSFODNN7EXAMPLE\n");
            git.add().addFilepattern("src/config.properties").call();
            git.commit().setAuthor(devAliceRecent).setCommitter(devAliceRecent).setMessage("Re-add AWS credentials").call();
        }

        // Create connection pointing to synthetic repo
        SourceConnectionEntity connection = new SourceConnectionEntity();
        connection.setId("src-git-history-test");
        connection.setTenantId(tenantId);
        connection.setType("GITHUB");
        connection.setDisplayName("Test Git Repo");
        connection.setStatus("ACTIVE");

        // Scope to repoDir
        SourceScopeEntity scope = new SourceScopeEntity();
        scope.setId("scope-git-1");
        scope.setSourceConnection(connection);
        scope.setExternalId(repoDir.getAbsolutePath());
        scope.setScopeType("REPOSITORY");
        scope.setScanHistory(true);
        connection.getScopes().add(scope);

        connectionRepository.save(connection);

        // Run full sync history scan
        orchestratorService.startScanSync(connection.getId(), ScanMode.FULL);

        List<FindingEntity> findings = findingRepository.findByTenantId(tenantId);
        FindingEntity finding = findings.stream()
                .filter(f -> "AWS_ACCESS_KEY".equals(f.getSecretType()))
                .findFirst()
                .orElseThrow();

        assertThat(finding.getMaskedSecret()).isEqualTo("AKIA••••••••MPLE");
        assertThat(finding.getIntroducedByJson()).contains("Alice Developer");
        assertThat(finding.getIntroducedByJson()).contains("alice@enterprise.com");
        assertThat(finding.getOccurrences().size()).isGreaterThanOrEqualTo(2);
    }
}
