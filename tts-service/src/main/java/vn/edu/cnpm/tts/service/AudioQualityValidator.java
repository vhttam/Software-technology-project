package vn.edu.cnpm.tts.service;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import vn.edu.cnpm.tts.config.LanguageRegistry;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class AudioQualityValidator {

    private final Environment env;
    private final boolean ffmpegInstalled;

    public AudioQualityValidator(Environment env) {
        this.env = env;
        this.ffmpegInstalled = checkBinary("ffmpeg") && checkBinary("ffprobe");
    }

    public boolean isFfmpegInstalled() {
        return ffmpegInstalled;
    }

    public boolean isHealthyForCurrentMode() {
        String provider = env.getProperty("app.tts.provider", env.getProperty("PROVIDER_MODE", "stub"));
        if ("real".equalsIgnoreCase(provider)) {
            return ffmpegInstalled;
        }
        return true;
    }

    public String getUnhealthyReason() {
        if (!isHealthyForCurrentMode()) {
            return "Thiếu công cụ xử lý âm thanh ffmpeg/ffprobe trong chế độ real";
        }
        return null;
    }

    public boolean validate(String lang, int charCount, double durationSec, File file) {
        if (file == null || !file.exists() || file.length() < 500) {
            return false;
        }

        LanguageRegistry.LanguageDef def = LanguageRegistry.findByCode(lang);
        double maxSpeed = (def != null && def.maxReadingSpeed() > 0) ? def.maxReadingSpeed() : 20.0;
        double minExpectedDuration = (double) charCount / maxSpeed;
        double threshold = 0.40 * minExpectedDuration;

        if (ffmpegInstalled) {
            return validateWithFfmpeg(file, threshold);
        }

        // Fallback for mock/test profile without ffmpeg
        return validateWithByteInspection(file, durationSec, threshold);
    }

    private boolean validateWithFfmpeg(File file, double minDurationThreshold) {
        try {
            // 1. Probe duration with ffprobe
            ProcessBuilder pbProbe = new ProcessBuilder(
                    "ffprobe", "-v", "error",
                    "-show_entries", "format=duration",
                    "-of", "default=noprint_wrappers=1:nokey=1",
                    file.getAbsolutePath()
            );
            Process probeProcess = pbProbe.start();
            double duration = 0.0;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(probeProcess.getInputStream()))) {
                String line = reader.readLine();
                if (line != null && !line.isBlank()) {
                    duration = Double.parseDouble(line.trim());
                }
            }
            probeProcess.waitFor();
            if (duration < minDurationThreshold) {
                return false;
            }

            // 2. Detect silence and volume with ffmpeg
            ProcessBuilder pbFfmpeg = new ProcessBuilder(
                    "ffmpeg", "-i", file.getAbsolutePath(),
                    "-af", "silencedetect=noise=-30dB:d=0.5,volumedetect",
                    "-f", "null", "-"
            );
            Process ffmpegProcess = pbFfmpeg.start();
            double totalSilence = 0.0;
            double maxVolume = -999.0;
            Pattern silencePattern = Pattern.compile("silence_duration:\\s*([0-9.]+)");
            Pattern volumePattern = Pattern.compile("max_volume:\\s*(-?[0-9.]+)\\s*dB");
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(ffmpegProcess.getErrorStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    Matcher m = silencePattern.matcher(line);
                    if (m.find()) {
                        totalSilence += Double.parseDouble(m.group(1));
                    }
                    Matcher mv = volumePattern.matcher(line);
                    if (mv.find()) {
                        maxVolume = Double.parseDouble(mv.group(1));
                    }
                }
            }
            ffmpegProcess.waitFor();

            if (maxVolume < -45.0 && maxVolume != -999.0) {
                return false;
            }

            double silenceRatio = duration > 0 ? (totalSilence / duration) : 1.0;
            return silenceRatio <= 0.40;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean validateWithByteInspection(File file, double durationSec, double minDurationThreshold) {
        try {
            double actualDuration = durationSec > 0 ? durationSec : ((double) file.length() / 15429.0);
            if (actualDuration < minDurationThreshold) {
                return false;
            }

            byte[] bytes = Files.readAllBytes(file.toPath());
            if (bytes.length < 500) return false;

            int zeroCount = 0;
            for (byte b : bytes) {
                if (b == 0) zeroCount++;
            }
            double zeroRatio = (double) zeroCount / bytes.length;
            return zeroRatio <= 0.40;
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean checkBinary(String binaryName) {
        try {
            Process p = new ProcessBuilder(binaryName, "-version").redirectErrorStream(true).start();
            boolean ok = p.waitFor(3, java.util.concurrent.TimeUnit.SECONDS);
            return ok && p.exitValue() == 0;
        } catch (Exception ignored) {
            return false;
        }
    }
}
