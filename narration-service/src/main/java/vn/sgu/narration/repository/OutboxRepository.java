package vn.sgu.narration.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.sgu.narration.domain.OutboxEntity;

import java.util.List;
import java.util.UUID;

public interface OutboxRepository extends JpaRepository<OutboxEntity, UUID> {
    List<OutboxEntity> findTop100BySentAtIsNullOrderByCreatedAtAsc();
}
