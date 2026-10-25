package vn.edu.cnpm.translation.provider;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class GoogleTranslationProviderMockServerTest {

    private MockWebServer googleServer;
    private MockWebServer myMemoryServer;

    @BeforeEach
    void setUp() throws IOException {
        googleServer = new MockWebServer();
        myMemoryServer = new MockWebServer();
        googleServer.start();
        myMemoryServer.start();
    }

    @AfterEach
    void tearDown() throws IOException {
        googleServer.shutdown();
        myMemoryServer.shutdown();
    }

    @Test
    void whenGoogleReturns503ThenFallsBackToMyMemorySuccessfully() {
        // Google returns 503 Service Unavailable
        googleServer.enqueue(new MockResponse().setResponseCode(503).setBody("Service Unavailable"));

        // MyMemory fallback returns 200 with translatedText
        myMemoryServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"responseData\":{\"translatedText\":\"Hello world\"}}"));

        GoogleTranslationProvider provider = new GoogleTranslationProvider(
                googleServer.url("").toString().replaceAll("/$", ""),
                myMemoryServer.url("").toString().replaceAll("/$", "")
        );

        String result = provider.translate("Xin chào thế giới", "vi", "en");
        assertThat(result).isEqualTo("Hello world");
    }

    @Test
    void whenGoogleReturns429ThenFallsBackToMyMemorySuccessfully() {
        // Google returns 429 Too Many Requests (Rate limit)
        googleServer.enqueue(new MockResponse().setResponseCode(429).setBody("Too Many Requests"));

        // MyMemory fallback returns 200 with translatedText
        myMemoryServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"responseData\":{\"translatedText\":\"Historical museum\"}}"));

        GoogleTranslationProvider provider = new GoogleTranslationProvider(
                googleServer.url("").toString().replaceAll("/$", ""),
                myMemoryServer.url("").toString().replaceAll("/$", "")
        );

        String result = provider.translate("Bảo tàng lịch sử", "vi", "en");
        assertThat(result).isEqualTo("Historical museum");
    }

    @Test
    void whenGoogleTimesOutThenFallsBackToMyMemory() {
        // Google delays headers beyond the 12s client timeout, but in test we test socket timeout by setting delay
        // or disconnect to simulate network hang
        googleServer.enqueue(new MockResponse().setSocketPolicy(okhttp3.mockwebserver.SocketPolicy.NO_RESPONSE));

        myMemoryServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"responseData\":{\"translatedText\":\"Ancient relic\"}}"));

        GoogleTranslationProvider provider = new GoogleTranslationProvider(
                googleServer.url("").toString().replaceAll("/$", ""),
                myMemoryServer.url("").toString().replaceAll("/$", "")
        );

        String result = provider.translate("Cổ vật", "vi", "en");
        assertThat(result).isEqualTo("Ancient relic");
    }

    @Test
    void throttleEnforcesMinimumIntervalBetweenSuccessiveCalls() {
        googleServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("[[[\"One\",\"Một\",null,null,10]]]"));
        googleServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("[[[\"Two\",\"Hai\",null,null,10]]]"));

        GoogleTranslationProvider provider = new GoogleTranslationProvider(
                googleServer.url("").toString().replaceAll("/$", ""),
                myMemoryServer.url("").toString().replaceAll("/$", "")
        );

        long start = System.currentTimeMillis();
        provider.translate("Một", "vi", "en");
        provider.translate("Hai", "vi", "en");
        long elapsed = System.currentTimeMillis() - start;

        // Two calls must take at least MIN_CALL_INTERVAL_MS (400ms)
        assertThat(elapsed).isGreaterThanOrEqualTo(GoogleTranslationProvider.getMinCallIntervalMs() - 50);
    }
}
