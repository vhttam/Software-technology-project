package vn.sgu.narration.api;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import vn.sgu.narration.config.RabbitConfiguration;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/dev/mock")
@ConditionalOnProperty(name = "app.mocks.enabled", havingValue = "true")
public class DevEventController {
    private final RabbitTemplate rabbit;
    private final ObjectMapper mapper;

    public DevEventController(RabbitTemplate rabbit, ObjectMapper mapper) {
        this.rabbit = rabbit;
        this.mapper = mapper;
    }

    @PostMapping("/events/{eventType}")
    public ApiEnvelope<JsonNode> publish(@PathVariable String eventType, @RequestBody JsonNode payload,
                                         @RequestHeader(value = "X-Correlation-ID", required = false) String correlationId) {
        String routingKey = switch (eventType) {
            case "translation-completed" -> "translation.completed";
            case "translation-failed" -> "translation.failed";
            case "tts-completed" -> "tts.completed";
            case "tts-failed" -> "tts.failed";
            default -> throw new AppException(HttpStatus.BAD_REQUEST, "INVALID_EVENT", "Loại event mock không hợp lệ");
        };
        String eventId = UUID.randomUUID().toString();
        String correlation = correlationId == null || correlationId.isBlank() ? UUID.randomUUID().toString() : correlationId;
        try {
            rabbit.convertAndSend(RabbitConfiguration.EVENTS_EXCHANGE, routingKey, mapper.writeValueAsString(payload), message -> {
                message.getMessageProperties().setContentType("application/json");
                message.getMessageProperties().setHeader("eventId", eventId);
                message.getMessageProperties().setHeader("correlationId", correlation);
                message.getMessageProperties().setHeader("schemaVersion", "1");
                message.getMessageProperties().setHeader("occurredAt", Instant.now().toString());
                return message;
            });
        } catch (Exception ex) {
            throw new AppException(HttpStatus.SERVICE_UNAVAILABLE, "MESSAGE_BROKER_UNAVAILABLE",
                    "Không thể phát event mẫu lên RabbitMQ");
        }
        return ApiEnvelope.success(payload);
    }
}
