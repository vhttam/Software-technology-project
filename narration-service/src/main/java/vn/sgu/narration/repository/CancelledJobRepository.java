package vn.sgu.narration.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.sgu.narration.domain.CancelledJobEntity;

import java.time.Instant;
import java.util.List;

public interface CancelledJobRepository extends JpaRepository<CancelledJobEntity, String> {
    long deleteByExpiresAtBefore(Instant now);
}
