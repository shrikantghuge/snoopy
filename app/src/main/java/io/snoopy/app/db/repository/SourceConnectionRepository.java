package io.snoopy.app.db.repository;

import io.snoopy.app.db.entity.SourceConnectionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface SourceConnectionRepository extends JpaRepository<SourceConnectionEntity, String> {
    List<SourceConnectionEntity> findByTenantId(String tenantId);
}
