package vn.sgu.narration.integration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.client.RestClient;
import vn.sgu.narration.api.Contracts;

@Component
public class GatewayProgressCallback {
    private static final Logger log = LoggerFactory.getLogger(GatewayProgressCallback.class);
    private final RestClient gateway;
    private final String serviceToken;

    public GatewayProgressCallback(RestClient.Builder builder,
            @Value("${app.downstream.api-gateway-url}") String gatewayUrl,
            @Value("${app.downstream.service-token}") String serviceToken) {
        this.gateway = builder.baseUrl(gatewayUrl).build();
        this.serviceToken = serviceToken;
    }

    @TransactionalEventListener
    public void sendAfterCommit(Contracts.JobProgressEvent event) {
        try {
            gateway.post().uri("/internal/callbacks/job-progress")
                    .header("X-Service-Token", serviceToken)
                    .header("X-Correlation-ID", event.correlationId())
                    .body(event).retrieve().toBodilessEntity();
        } catch (Exception ex) {
            log.warn("Best-effort progress callback failed jobId={}", event.data().jobId(), ex);
        }
    }
}
