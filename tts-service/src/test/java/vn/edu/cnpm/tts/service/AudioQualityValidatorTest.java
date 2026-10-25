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

    @Test
    void realFfmpegValidationOnThreeAudioSamples() throws Exception {
        org.junit.jupiter.api.Assumptions.assumeTrue(
                validator.isFfmpegInstalled(),
                "Bỏ qua test FFmpeg thật trên máy local vì chưa cài ffmpeg/ffprobe (sẽ chạy đầy đủ trên môi trường CI)"
        );

        // 1. Bình thường: tạo file audio hợp lệ bằng ffmpeg phát âm tần 440Hz dài 4 giây
        File normalFile = File.createTempFile("ffmpeg_normal_", ".mp3");
        // 2. Toàn im lặng: tạo file audio im lặng (anullsrc) dài 4 giây
        File silentFile = File.createTempFile("ffmpeg_silent_", ".mp3");
        // 3. Bị cắt cụt: tạo file audio ngắn 0.5 giây
        File truncFile = File.createTempFile("ffmpeg_truncated_", ".mp3");

        try {
            // Sinh file bình thường bằng ffmpeg
            Process p1 = new ProcessBuilder("ffmpeg", "-y", "-f", "lavfi", "-i", "sine=frequency=440:duration=4",
                    "-c:a", "libmp3lame", "-b:a", "128k", normalFile.getAbsolutePath())
                    .redirectErrorStream(true).start();
            p1.waitFor(10, java.util.concurrent.TimeUnit.SECONDS);

            // Sinh file im lặng bằng ffmpeg
            Process p2 = new ProcessBuilder("ffmpeg", "-y", "-f", "lavfi", "-i", "anullsrc=r=44100:cl=mono",
                    "-t", "4", "-c:a", "libmp3lame", "-b:a", "128k", silentFile.getAbsolutePath())
                    .redirectErrorStream(true).start();
            p2.waitFor(10, java.util.concurrent.TimeUnit.SECONDS);

            // Sinh file bị cắt cụt (0.5 giây) bằng ffmpeg
            Process p3 = new ProcessBuilder("ffmpeg", "-y", "-f", "lavfi", "-i", "sine=frequency=440:duration=0.5",
                    "-c:a", "libmp3lame", "-b:a", "128k", truncFile.getAbsolutePath())
                    .redirectErrorStream(true).start();
            p3.waitFor(10, java.util.concurrent.TimeUnit.SECONDS);

            // Kiểm tra:
            // File bình thường: văn bản 30 ký tự tiếng Anh (~1.6s kỳ vọng, ngưỡng 0.64s), file dài 4s -> Hợp lệ (true)
            boolean normalValid = validator.validate("en", 30, 4.0, normalFile);
            assertThat(normalValid).isTrue();

            // File im lặng: toàn bộ âm lượng <-45dB hoặc silence_ratio > 40% -> Không hợp lệ (false)
            boolean silentValid = validator.validate("en", 30, 4.0, silentFile);
            assertThat(silentValid).isFalse();

            // File bị cắt cụt: văn bản 300 ký tự (~16.6s kỳ vọng, ngưỡng 6.6s), file chỉ có 0.5s -> Không hợp lệ (false)
            boolean truncValid = validator.validate("en", 300, 0.5, truncFile);
            assertThat(truncValid).isFalse();
        } finally {
            normalFile.delete();
            silentFile.delete();
            truncFile.delete();
        }
    }
}
