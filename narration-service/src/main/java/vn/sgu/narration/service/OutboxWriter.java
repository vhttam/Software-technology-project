package vn.sgu.narration.service;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import vn.sgu.narration.domain.OutboxEntity;
import vn.sgu.narration.repository.OutboxRepository;

import java.time.Instant;

@Component
public class OutboxWriter {
    private final OutboxRepository repository;
    private final ObjectMapper objectMapper;

    public OutboxWriter(OutboxRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    public void append(String exchange, String routingKey, String eventType,
                       String correlationId, Object payload, Instant now) {
        try {
            repository.save(new OutboxEntity(exchange, routingKey, eventType,
                    correlationId, objectMapper.writeValueAsString(payload), now));
        } catch (JacksonException ex) {
            throw new IllegalStateException("Không thể tạo payload outbox", ex);
        }
    }
}
