package io.snoopy.app.db.repository;

import io.snoopy.app.db.entity.FindingEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface FindingRepository extends JpaRepository<FindingEntity, String> {
    List<FindingEntity> findByTenantId(String tenantId);
    Optional<FindingEntity> findByTenantIdAndFingerprintId(String tenantId, String fingerprintId);
}
