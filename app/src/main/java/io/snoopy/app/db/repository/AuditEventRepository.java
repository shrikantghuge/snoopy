package io.snoopy.app.db.repository;

import io.snoopy.app.db.entity.AuditEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface AuditEventRepository extends JpaRepository<AuditEventEntity, String> {
    List<AuditEventEntity> findByTenantIdOrderByTimestampDesc(String tenantId);
}
