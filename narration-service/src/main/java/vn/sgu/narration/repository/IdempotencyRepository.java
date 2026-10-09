package vn.sgu.narration.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import vn.sgu.narration.domain.IdempotencyEntity;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface IdempotencyRepository extends JpaRepository<IdempotencyEntity, UUID> {
    Optional<IdempotencyEntity> findByUserIdAndOperationAndIdempotencyKey(
            String userId, String operation, String idempotencyKey);
    List<IdempotencyEntity> findByJobId(String jobId);
    @Modifying
    @Transactional
    @Query(value = "DELETE i FROM idempotency_records i " +
            "INNER JOIN narration_jobs j ON j.job_id = i.job_id " +
            "WHERE i.expires_at < :cutoff " +
            "AND j.status IN ('COMPLETED','PARTIALLY_COMPLETED','FAILED','CANCELLED')", nativeQuery = true)
    int deleteExpiredForTerminalJobs(@Param("cutoff") Instant cutoff);
    long deleteByJobId(String jobId);
}
