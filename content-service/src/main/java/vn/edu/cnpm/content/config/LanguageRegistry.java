package vn.edu.cnpm.content.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.util.*;
import java.util.regex.Pattern;

public final class LanguageRegistry {
    private LanguageRegistry() {}

    private static final Pattern DIACRITICS = Pattern.compile("\\p{InCombiningDiacriticalMarks}+");
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public record VoiceOption(String voiceId, String gender, String type, String label, boolean isDefault) {}

    public record LanguageDef(
            String code,
            String nameVi,
            String nativeName,
            String translationLocale,
            String ttsLocale,
            String direction,
            String fontStack,
            String sentenceDelimiters,
            String clauseDelimiters,
            double minReadingSpeed,
            double maxReadingSpeed,
            int maxTtsChunkChars,
            int maxTtsChunkBytes,
            int sortOrder,
            boolean enabled,
            String note,
            String disabledReason,
            List<VoiceOption> voices
    ) {}

    private static volatile List<LanguageDef> allLanguages = loadLanguages();

    public static synchronized void reload() {
        allLanguages = loadLanguages();
    }

    private static List<LanguageDef> loadLanguages() {
        File configFile = resolveConfigFile();
        if (configFile != null && configFile.exists()) {
            try {
                String json = Files.readString(configFile.toPath());
                List<LanguageDef> list = parseLanguages(json);
                if (!list.isEmpty()) {
                    return list;
                }
            } catch (Exception e) {
                System.err.println("[LanguageRegistry] Failed to parse " + configFile + ": " + e.getMessage());
            }
        }
        return getDefaultFallback();
    }

    private static File resolveConfigFile() {
        String envPath = System.getenv("LANGUAGE_CONFIG_PATH");
        if (envPath != null && !envPath.isBlank()) {
            File f = new File(envPath);
            if (f.exists()) return f;
        }
        String[] candidates = {
                "config/languages.json",
                "../config/languages.json",
                "../../config/languages.json",
                "../../../config/languages.json"
        };
        for (String c : candidates) {
            File f = new File(c);
            if (f.exists()) return f;
        }
        return null;
    }

    public static List<LanguageDef> parseLanguages(String json) {
        try {
            JsonNode root = MAPPER.readTree(json);
            if (!root.isArray()) return List.of();
            List<LanguageDef> list = new ArrayList<>();
            for (JsonNode node : root) {
                String code = node.path("code").asText("");
                if (code.isBlank()) continue;
                String nameVi = node.path("nameVi").asText(code);
                String nativeName = node.path("nativeName").asText(nameVi);
                String translationLocale = node.path("translationLocale").asText(code);
                String ttsLocale = node.path("ttsLocale").asText(code);
                String direction = node.path("direction").asText("ltr");
                String fontStack = node.path("fontStack").asText("'Times New Roman', Times, serif");
                String sentenceDelimiters = node.path("sentenceDelimiters").asText("[.!?;\n]");
                String clauseDelimiters = node.path("clauseDelimiters").asText("[,]");
                double minReadingSpeed = node.path("minReadingSpeed").asDouble(10.0);
                double maxReadingSpeed = node.path("maxReadingSpeed").asDouble(20.0);
                int maxTtsChunkChars = node.path("maxTtsChunkChars").asInt(80);
                int maxTtsChunkBytes = node.path("maxTtsChunkBytes").asInt(120);
                int sortOrder = node.path("sortOrder").asInt(99);
                boolean enabled = node.path("enabled").asBoolean(true);
                String note = node.path("note").isNull() ? null : node.path("note").asText(null);
                String disabledReason = node.path("disabledReason").isNull() ? null : node.path("disabledReason").asText(null);

                List<VoiceOption> voices = new ArrayList<>();
                JsonNode voicesNode = node.path("voices");
                if (voicesNode.isArray()) {
                    for (JsonNode vn : voicesNode) {
                        voices.add(new VoiceOption(
                                vn.path("voiceId").asText(""),
                                vn.path("gender").asText("FEMALE"),
                                vn.path("type").asText("Standard"),
                                vn.path("label").asText("Mặc định"),
                                vn.path("isDefault").asBoolean(false)
                        ));
                    }
                }
                list.add(new LanguageDef(
                        code, nameVi, nativeName, translationLocale, ttsLocale,
                        direction, fontStack, sentenceDelimiters, clauseDelimiters,
                        minReadingSpeed, maxReadingSpeed, maxTtsChunkChars, maxTtsChunkBytes,
                        sortOrder, enabled, note, disabledReason, voices
                ));
            }
            list.sort(Comparator.comparingInt(LanguageDef::sortOrder));
            return Collections.unmodifiableList(list);
        } catch (Exception e) {
            System.err.println("[LanguageRegistry] Error parsing JSON: " + e.getMessage());
            return List.of();
        }
    }

    private static List<LanguageDef> getDefaultFallback() {
        return List.of(
                new LanguageDef("vi", "Tiếng Việt", "Tiếng Việt", "vi", "vi-VN", "ltr", "'Times New Roman', Times, serif",
                        "[.!?;\n]", "[,]", 12.0, 25.0, 80, 120, 1, true, null, null,
                        List.of(new VoiceOption("vi-VN-Standard-A", "FEMALE", "Standard", "Giọng nữ (khuyên dùng)", true),
                                new VoiceOption("vi-VN-Standard-B", "MALE", "Standard", "Giọng nam", false))),
                new LanguageDef("en", "Tiếng Anh", "English", "en", "en-US", "ltr", "'Times New Roman', Times, serif",
                        "[.!?;\n]", "[,]", 12.0, 18.0, 80, 120, 2, true, null, null,
                        List.of(new VoiceOption("en-US-Journey-F", "FEMALE", "Journey", "Giọng nữ (khuyên dùng)", true),
                                new VoiceOption("en-US-Standard-B", "MALE", "Standard", "Giọng nam", false))),
                new LanguageDef("fr", "Tiếng Pháp", "Français", "fr", "fr-FR", "ltr", "'Times New Roman', Times, serif",
                        "[.!?;\n]", "[,]", 12.0, 18.0, 80, 120, 3, true, null, null,
                        List.of(new VoiceOption("fr-FR-Standard-A", "FEMALE", "Standard", "Giọng nữ (khuyên dùng)", true),
                                new VoiceOption("fr-FR-Standard-B", "MALE", "Standard", "Giọng nam", false))),
                new LanguageDef("ja", "Tiếng Nhật", "日本語", "ja", "ja", "ltr", "'Yu Gothic', 'Hiragino Sans', 'Meiryo', sans-serif",
                        "[.!?;\n。！？；]", "[,，、]", 5.0, 9.0, 80, 120, 4, true, null, null,
                        List.of(new VoiceOption("ja-JP-Standard-A", "FEMALE", "Standard", "Giọng nữ (khuyên dùng)", true),
                                new VoiceOption("ja-JP-Standard-C", "MALE", "Standard", "Giọng nam", false))),
                new LanguageDef("zh", "Tiếng Trung", "中文", "zh-CN", "zh-CN", "ltr", "'PingFang SC', 'Microsoft YaHei', 'SimHei', sans-serif",
                        "[.!?;\n。！？；]", "[,，、]", 3.0, 7.0, 80, 120, 5, true, null, null,
                        List.of(new VoiceOption("cmn-CN-Standard-A", "FEMALE", "Standard", "Giọng nữ (khuyên dùng)", true),
                                new VoiceOption("cmn-CN-Standard-B", "MALE", "Standard", "Giọng nam", false))),
                new LanguageDef("ko", "Tiếng Hàn", "한국어", "ko", "ko", "ltr", "'Malgun Gothic', 'Apple SD Gothic Neo', 'Nanum Gothic', sans-serif",
                        "[.!?;\n。！？；]", "[,，、]", 5.0, 9.0, 80, 120, 6, true, null, null,
                        List.of(new VoiceOption("ko-KR-Standard-A", "FEMALE", "Standard", "Giọng nữ (khuyên dùng)", true),
                                new VoiceOption("ko-KR-Standard-C", "MALE", "Standard", "Giọng nam", false)))
        );
    }

    public static List<LanguageDef> getAllLanguages() {
        return allLanguages;
    }

    public static List<LanguageDef> getSupportedLanguages() {
        return allLanguages.stream().filter(LanguageDef::enabled).toList();
    }

    public static List<String> getLanguageCodes() {
        return getSupportedLanguages().stream().map(LanguageDef::code).toList();
    }

    public static List<String> getAllLanguageCodes() {
        return allLanguages.stream().map(LanguageDef::code).toList();
    }

    public static boolean isSupported(String code) {
        if (code == null) return false;
        return getSupportedLanguages().stream().anyMatch(l -> l.code().equalsIgnoreCase(code.trim()));
    }

    public static LanguageDef findByCode(String code) {
        if (code == null) return null;
        return allLanguages.stream()
                .filter(l -> l.code().equalsIgnoreCase(code.trim()))
                .findFirst()
                .orElse(null);
    }

    public static List<Map<String, Object>> getSupportedVoicesResponse() {
        return getSupportedLanguages().stream().map(l -> Map.<String, Object>of(
                "lang", l.code(),
                "voices", l.voices().stream().map(VoiceOption::voiceId).toList()
        )).toList();
    }

    public static String unaccent(String text) {
        if (text == null) return "";
        String normalized = Normalizer.normalize(text, Normalizer.Form.NFD);
        String withoutDiacritics = DIACRITICS.matcher(normalized).replaceAll("");
        return withoutDiacritics.replace('đ', 'd').replace('Đ', 'D').toLowerCase().trim();
    }
}
