package vn.sgu.narration.integration;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import vn.sgu.narration.api.AppException;
import vn.sgu.narration.api.Contracts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NarrationDependenciesMockTest {
    private final NarrationDependencies dependencies = new NarrationDependencies(
            org.springframework.web.client.RestClient.builder(),
            "http://content.test", "http://translation.test", "http://tts.test", "internal-token", true);

    @Test
    void mocksContentVoiceTranslationAndSignedUrlDependencies() {
        assertThat(dependencies.validateContentForJob("content-1", "corr-1"))
                .isEqualTo(new Contracts.ContentCheck("content-1", 3, false));
        assertThat(dependencies.voices("corr-1").supportedVoices())
                .anySatisfy(group -> assertThat(group.lang()).isEqualTo("en"));
        assertThat(dependencies.translation("tr-1", "content-1", 3, "vi", "corr-1"))
                .isEqualTo(new Contracts.TranslationView("tr-1", "content-1", 3, "vi",
                        "Mock translated subtitle for tr-1"));
        Contracts.SignedUrl signedUrl = dependencies.signedUrl("audio/one", "corr-1");
        assertThat(signedUrl.audioUrl()).contains("audio-one.mp3");
        assertThat(signedUrl.expiresAt()).isAfter(java.time.Instant.now());
    }

    @Test
    void mockContentPreservesMissingAndDeletedSemantics() {
        assertThatThrownBy(() -> dependencies.validateContentForJob("missing-content", "corr-1"))
                .isInstanceOfSatisfying(AppException.class, ex -> {
                    assertThat(ex.status()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(ex.errorCode()).isEqualTo("CONTENT_NOT_FOUND");
                });
        assertThat(dependencies.validateContentForJob("deleted-content", "corr-1").isDeleted()).isTrue();
    }
}
