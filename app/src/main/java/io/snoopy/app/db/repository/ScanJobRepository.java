package io.snoopy.app.db.repository;

import io.snoopy.app.db.entity.ScanJobEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ScanJobRepository extends JpaRepository<ScanJobEntity, String> {
    List<ScanJobEntity> findByTenantId(String tenantId);
}
