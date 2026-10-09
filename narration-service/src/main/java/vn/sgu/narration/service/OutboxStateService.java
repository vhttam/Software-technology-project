package vn.sgu.narration.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.sgu.narration.domain.OutboxEntity;
import vn.sgu.narration.repository.OutboxRepository;

import java.time.Instant;
import java.util.UUID;

@Service
public class OutboxStateService {
    private final OutboxRepository repository;

    public OutboxStateService(OutboxRepository repository) { this.repository = repository; }

    @Transactional
    public void sent(UUID id) {
        repository.findById(id).ifPresent(event -> event.markSent(Instant.now()));
    }

    @Transactional
    public void failed(UUID id, String reason) {
        repository.findById(id).ifPresent(event -> event.markAttemptFailed(reason));
    }
}
