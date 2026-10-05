package io.snoopy.app.db.repository;

import io.snoopy.app.db.entity.SecretFingerprintEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface SecretFingerprintRepository extends JpaRepository<SecretFingerprintEntity, String> {
    Optional<SecretFingerprintEntity> findByTenantIdAndFingerprint(String tenantId, String fingerprint);
}
