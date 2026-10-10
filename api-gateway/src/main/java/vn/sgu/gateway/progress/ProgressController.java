package vn.sgu.gateway.progress;

import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import vn.sgu.gateway.progress.ProgressContracts.JobProgressEvent;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@RestController
public class ProgressController {
    private final ProgressHub hub;
    private final byte[] expectedToken;

    public ProgressController(ProgressHub hub,
            @Value("${gateway.service-tokens.narration}") String internalToken) {
        this.hub = hub;
        this.expectedToken = internalToken.getBytes(StandardCharsets.UTF_8);
    }

    @GetMapping(value = "/api/v1/jobs/{jobId}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<org.springframework.http.codec.ServerSentEvent<JobProgressEvent>> stream(@PathVariable String jobId) {
        return hub.stream(jobId);
    }

    @PostMapping("/internal/callbacks/job-progress")
    public Mono<ResponseEntity<Void>> callback(
            @RequestHeader(value = "X-Service-Token", required = false) String token,
            @Valid @RequestBody JobProgressEvent event) {
        if (!StringUtils.hasText(token) || !MessageDigest.isEqual(expectedToken, token.getBytes(StandardCharsets.UTF_8))) {
            return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
        }
        hub.publish(event);
        return Mono.just(ResponseEntity.ok().build());
    }
}
