package vn.edu.cnpm.tts.provider;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import vn.edu.cnpm.tts.config.LanguageRegistry;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

@Component
@ConditionalOnProperty(name = "app.tts.provider", havingValue = "stub", matchIfMissing = true)
public class StubTtsProvider implements TtsProvider {

    private static final byte[] BASE_SAMPLE = loadBaseSample();

    private static byte[] loadBaseSample() {
        try (InputStream in = StubTtsProvider.class.getResourceAsStream("/synthetic_beep_1s.mp3")) {
            if (in != null) {
                return in.readAllBytes();
            }
        } catch (Exception ignored) {}
        return generateSyntheticToneFrames(40);
    }

    @Override
    public byte[] synthesize(String text, String lang, String voice) {
        int charCount = (text != null && !text.isBlank()) ? text.length() : 40;
        LanguageRegistry.LanguageDef def = LanguageRegistry.findByCode(lang);
        double maxSpeed = (def != null && def.maxReadingSpeed() > 0) ? def.maxReadingSpeed() : 15.0;
        double durationSec = Math.max(1.0, (double) charCount / maxSpeed);

        byte[] sample = BASE_SAMPLE;
        if (sample != null && sample.length > 500) {
            int repeats = Math.max(1, (int) Math.ceil(durationSec));
            ByteArrayOutputStream out = new ByteArrayOutputStream(repeats * sample.length);
            for (int i = 0; i < repeats; i++) {
                out.write(sample, 0, sample.length);
            }
            return out.toByteArray();
        }

        // Fallback: synthetic beep frames (MPEG-1 Layer 3, ~38 frames per sec)
        return generateSyntheticToneFrames((int) Math.ceil(durationSec * 38.28));
    }

    private static byte[] generateSyntheticToneFrames(int frameCount) {
        // MPEG-1 Layer 3, 128 kbps, 44100 Hz, mono: frame size 417 bytes
        byte[] frame = new byte[417];
        frame[0] = (byte) 0xFF;
        frame[1] = (byte) 0xFB;
        frame[2] = (byte) 0x90;
        frame[3] = (byte) 0x64;
        for (int i = 4; i < 417; i++) {
            frame[i] = (byte) ((i * 37) & 0xFF);
        }

        byte[] result = new byte[frameCount * 417];
        for (int f = 0; f < frameCount; f++) {
            System.arraycopy(frame, 0, result, f * 417, 417);
        }
        return result;
    }
}
