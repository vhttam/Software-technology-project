package vn.sgu.narration.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import vn.sgu.narration.integration.NarrationDependencies;

@RestController
@RequestMapping("/api/v1/voices")
public class VoiceController {
    private final NarrationDependencies dependencies;
    public VoiceController(NarrationDependencies dependencies) { this.dependencies = dependencies; }

    @GetMapping
    public ApiEnvelope<Contracts.VoiceCatalog> list(
            @RequestHeader(value = "X-Correlation-ID", required = false) String correlationId) {
        return ApiEnvelope.success(dependencies.voices(correlationId));
    }
}
