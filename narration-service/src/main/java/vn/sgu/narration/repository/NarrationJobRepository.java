package vn.sgu.narration.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.sgu.narration.domain.JobStatus;
import vn.sgu.narration.domain.NarrationJobEntity;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface NarrationJobRepository extends JpaRepository<NarrationJobEntity, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select j from NarrationJobEntity j where j.jobId = :jobId")
    Optional<NarrationJobEntity> findForUpdate(@Param("jobId") String jobId);

    List<NarrationJobEntity> findTop100ByStatusOrderByCreatedAtAsc(JobStatus status);

    @Query("select distinct j.jobId from NarrationJobEntity j join j.targets t " +
            "where j.status = :jobStatus and ((t.status = vn.sgu.narration.domain.TargetStatus.TRANSLATING and t.updatedAt < :translationBefore) " +
            "or (t.status = vn.sgu.narration.domain.TargetStatus.SYNTHESIZING and t.updatedAt < :synthesisBefore))")
    List<String> findTimedOutJobIds(@Param("jobStatus") JobStatus jobStatus,
                                   @Param("translationBefore") Instant translationBefore,
                                   @Param("synthesisBefore") Instant synthesisBefore,
                                   Pageable pageable);

    @Query("select distinct j from NarrationJobEntity j join fetch j.targets t " +
            "where j.contentId = :contentId and j.contentVersion = :version " +
            "and j.status in :statuses")
    List<NarrationJobEntity> findByContentVersionAndStatuses(@Param("contentId") String contentId,
                                                              @Param("version") int version,
                                                              @Param("statuses") List<JobStatus> statuses);

    @Query("select distinct j from NarrationJobEntity j join fetch j.targets t " +
            "where j.contentId = :contentId and j.status in :statuses")
    List<NarrationJobEntity> findByContentAndStatuses(@Param("contentId") String contentId,
                                                       @Param("statuses") List<JobStatus> statuses);

    @Query("select distinct j from NarrationJobEntity j join fetch j.targets t " +
            "where j.contentId = :contentId and j.status in :statuses order by j.contentVersion desc")
    List<NarrationJobEntity> findPublishedJobs(@Param("contentId") String contentId,
                                               @Param("statuses") List<JobStatus> statuses);
}
