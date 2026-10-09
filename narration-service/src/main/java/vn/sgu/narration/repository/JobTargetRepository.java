package vn.sgu.narration.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.sgu.narration.domain.JobTargetEntity;
import vn.sgu.narration.domain.TargetStatus;

import java.util.List;
import java.util.Optional;

public interface JobTargetRepository extends JpaRepository<JobTargetEntity, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from JobTargetEntity t where t.job.jobId = :jobId and t.targetId = :targetId")
    Optional<JobTargetEntity> findForUpdate(@Param("jobId") String jobId, @Param("targetId") String targetId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from JobTargetEntity t where t.job.jobId = :jobId order by t.targetId")
    List<JobTargetEntity> findAllForUpdate(@Param("jobId") String jobId);

    List<JobTargetEntity> findByJobJobIdOrderByCreatedAtAsc(String jobId);

    @Query("select t from JobTargetEntity t join fetch t.job j " +
            "where j.contentId = :contentId and t.status = :status " +
            "order by j.contentVersion desc, t.lang asc, t.updatedAt desc")
    List<JobTargetEntity> findPublishedByContent(@Param("contentId") String contentId,
                                                 @Param("status") TargetStatus status);

    boolean existsByJobJobIdAndLang(String jobId, String lang);
}
