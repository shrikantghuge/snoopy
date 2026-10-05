package io.snoopy.app.db.repository;

import io.snoopy.app.db.entity.FindingOccurrenceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface FindingOccurrenceRepository extends JpaRepository<FindingOccurrenceEntity, String> {
    List<FindingOccurrenceEntity> findByFindingId(String findingId);
}
