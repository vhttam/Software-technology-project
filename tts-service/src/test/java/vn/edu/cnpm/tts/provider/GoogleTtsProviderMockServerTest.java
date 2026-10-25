package vn.edu.cnpm.tts.provider;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GoogleTtsProviderMockServerTest {

    private MockWebServer ttsServer;

    @BeforeEach
    void setUp() throws IOException {
        ttsServer = new MockWebServer();
        ttsServer.start();
    }

    @AfterEach
    void tearDown() throws IOException {
        ttsServer.shutdown();
    }

    @Test
    void whenGoogleTtsReturns200WithSufficientAudioThenSucceeds() {
        byte[] dummyAudio = new byte[1024];
        for (int i = 0; i < dummyAudio.length; i++) dummyAudio[i] = (byte) (i % 128);

        ttsServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "audio/mpeg")
                .setBody(new okio.Buffer().write(dummyAudio)));

        GoogleTtsProvider provider = new GoogleTtsProvider(ttsServer.url("").toString().replaceAll("/$", ""));
        byte[] result = provider.synthesize("Welcome to the museum.", "en", "en-US-Journey-F");

        assertThat(result).hasSize(1024);
    }

    @Test
    void whenGoogleTtsReturns503ThenFailsOrThrowsExpectedException() {
        ttsServer.enqueue(new MockResponse().setResponseCode(503).setBody("Service Unavailable"));

        GoogleTtsProvider provider = new GoogleTtsProvider(ttsServer.url("").toString().replaceAll("/$", ""));
        // When online TTS returns 503 and on Windows fallback is attempted or fails, it throws RuntimeException
        try {
            byte[] audio = provider.synthesize("Xin chào quý khách.", "vi", "vi-VN-Standard-A");
            // If Windows TTS synthesized it, result is valid audio
            assertThat(audio).isNotEmpty();
        } catch (RuntimeException ex) {
            assertThat(ex.getMessage()).contains("TTS provider failed");
        }
    }

    @Test
    void whenGoogleTtsReturns429ThenFailsOrHandlesGracefully() {
        ttsServer.enqueue(new MockResponse().setResponseCode(429).setBody("Too Many Requests"));

        GoogleTtsProvider provider = new GoogleTtsProvider(ttsServer.url("").toString().replaceAll("/$", ""));
        try {
            byte[] audio = provider.synthesize("Xin chào quý khách.", "vi", "vi-VN-Standard-A");
            assertThat(audio).isNotEmpty();
        } catch (RuntimeException ex) {
            assertThat(ex.getMessage()).contains("TTS provider failed");
        }
    }

    @Test
    void whenGoogleTtsHangsThenTimesOutOrFails() {
        ttsServer.enqueue(new MockResponse().setSocketPolicy(okhttp3.mockwebserver.SocketPolicy.NO_RESPONSE));

        GoogleTtsProvider provider = new GoogleTtsProvider(ttsServer.url("").toString().replaceAll("/$", ""));
        try {
            byte[] audio = provider.synthesize("Xin chào quý khách.", "vi", "vi-VN-Standard-A");
            assertThat(audio).isNotEmpty();
        } catch (RuntimeException ex) {
            assertThat(ex.getMessage()).contains("TTS provider failed");
        }
    }

    @Test
    void throttleEnforcesMinimumIntervalBetweenChunks() {
        byte[] dummyAudio = new byte[600];
        ttsServer.enqueue(new MockResponse().setResponseCode(200).setBody(new okio.Buffer().write(dummyAudio)));
        ttsServer.enqueue(new MockResponse().setResponseCode(200).setBody(new okio.Buffer().write(dummyAudio)));

        GoogleTtsProvider provider = new GoogleTtsProvider(ttsServer.url("").toString().replaceAll("/$", ""));

        // Text with two sentences to force multiple chunks and invoke throttle
        String text = "First sentence to synthesize. Second sentence to synthesize.";
        long start = System.currentTimeMillis();
        byte[] result = provider.synthesize(text, "en", "en-US-Journey-F");
        long elapsed = System.currentTimeMillis() - start;

        assertThat(result).isNotEmpty();
        // Elapsed must respect minimum throttle interval
        assertThat(elapsed).isGreaterThanOrEqualTo(GoogleTtsProvider.getMinCallIntervalMs() - 50);
    }

    @Test
    void whenGoogleTtsFailsInRealModeThenThrowsExceptionAndNeverReturnsSyntheticBeep() {
        ttsServer.enqueue(new MockResponse().setResponseCode(503).setBody("Service Unavailable"));

        GoogleTtsProvider provider = new GoogleTtsProvider(ttsServer.url("").toString().replaceAll("/$", ""));
        // In real mode for a language without OS fallback (e.g., ja or fr), failure MUST throw RuntimeException
        assertThatThrownBy(() -> provider.synthesize("これはテストです。", "ja", "ja-JP-Standard-A"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("TTS provider failed to synthesize audio");
    }
}
