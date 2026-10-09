package vn.sgu.narration.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "outbox_events", indexes = @Index(name = "IX_outbox_pending", columnList = "sent_at,created_at"))
public class OutboxEntity {
    @Id
    @Column(name = "id", nullable = false)
    private UUID id;
    @Column(name = "event_id", nullable = false, unique = true)
    private UUID eventId;
    @Column(name = "exchange_name", length = 128, nullable = false)
    private String exchangeName;
    @Column(name = "routing_key", length = 128, nullable = false)
    private String routingKey;
    @Column(name = "event_type", length = 128, nullable = false)
    private String eventType;
    @Column(name = "correlation_id", length = 128, nullable = false)
    private String correlationId;
    @Column(name = "payload_json", columnDefinition = "nvarchar(max)", nullable = false)
    private String payloadJson;
    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "sent_at")
    private Instant sentAt;
    @Column(name = "attempts", nullable = false)
    private int attempts;
    @Column(name = "last_error", columnDefinition = "nvarchar(2000)")
    private String lastError;

    protected OutboxEntity() {}

    public OutboxEntity(String exchangeName, String routingKey, String eventType,
                        String correlationId, String payloadJson, Instant now) {
        this.id = UUID.randomUUID();
        this.eventId = UUID.randomUUID();
        this.exchangeName = exchangeName;
        this.routingKey = routingKey;
        this.eventType = eventType;
        this.correlationId = correlationId;
        this.payloadJson = payloadJson;
        this.occurredAt = now;
        this.createdAt = now;
    }

    public UUID getId() { return id; }
    public UUID getEventId() { return eventId; }
    public String getExchangeName() { return exchangeName; }
    public String getRoutingKey() { return routingKey; }
    public String getEventType() { return eventType; }
    public String getCorrelationId() { return correlationId; }
    public String getPayloadJson() { return payloadJson; }
    public Instant getOccurredAt() { return occurredAt; }
    public int getAttempts() { return attempts; }
    public void markSent(Instant now) { this.sentAt = now; }
    public void markAttemptFailed(String error) {
        attempts++;
        lastError = error == null ? "publisher confirm failed" : error.substring(0, Math.min(2000, error.length()));
    }
}
