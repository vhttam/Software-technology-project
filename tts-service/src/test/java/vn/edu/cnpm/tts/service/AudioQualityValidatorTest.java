package vn.edu.cnpm.tts.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import vn.edu.cnpm.tts.provider.TtsProvider;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class AudioQualityValidatorTest {

    @Autowired
    AudioQualityValidator validator;

    @Autowired
    TtsProvider provider;

    @Test
    void normalAudioPassesValidation() throws Exception {
        // 1. Bình thường: synthesize audio for a standard English sentence (~60 chars, expected duration ~3.3s)
        String text = "Welcome to the historical museum. Enjoy your guided tour.";
        byte[] audio = provider.synthesize(text, "en", "en-US-Journey-F");
        assertThat(audio).isNotEmpty();

        File tempFile = File.createTempFile("test_normal_", ".mp3");
        try {
            Files.write(tempFile.toPath(), audio);
            double estimatedDuration = (double) audio.length / 15429.0;
            boolean isValid = validator.validate("en", text.length(), estimatedDuration, tempFile);
            assertThat(isValid).isTrue();
        } finally {
            tempFile.delete();
        }
    }

    @Test
    void completelySilentAudioFailsValidation() throws Exception {
        // 2. Toàn im lặng: file with all zero bytes
        File tempFile = File.createTempFile("test_silent_", ".mp3");
        try {
            byte[] zeros = new byte[20000];
            Files.write(tempFile.toPath(), zeros);
            boolean isValid = validator.validate("en", 50, 4.0, tempFile);
            assertThat(isValid).isFalse();
        } finally {
            tempFile.delete();
        }
    }

    @Test
    void truncatedAudioFailsValidation() throws Exception {
        // 3. Bị cắt cụt: text has 300 characters in English (expected duration ~16.6s, threshold 40% = ~6.6s)
        // But audio provided is only 0.5 second (e.g. 500 bytes)
        StringBuilder longText = new StringBuilder();
        for (int i = 0; i < 30; i++) longText.append("Museum item ");
        assertThat(longText.length()).isGreaterThanOrEqualTo(300);

        File tempFile = File.createTempFile("test_truncated_", ".mp3");
        try {
            byte[] shortAudio = provider.synthesize("Hi", "en", "en-US-Journey-F");
            Files.write(tempFile.toPath(), shortAudio);
            // Pass longText length (300+ chars) but audio is tiny (duration ~1s)
            boolean isValid = validator.validate("en", longText.length(), 0.5, tempFile);
            assertThat(isValid).isFalse();
        } finally {
            tempFile.delete();
        }
    }
}
