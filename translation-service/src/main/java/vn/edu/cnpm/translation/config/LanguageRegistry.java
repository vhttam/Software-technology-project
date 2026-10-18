package vn.edu.cnpm.translation.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public final class LanguageRegistry {
    private LanguageRegistry() {}

    private static final ObjectMapper MAPPER = new ObjectMapper();

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
            String disabledReason
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

        List<Path> candidates = List.of(
                Paths.get("config/languages.json"),
                Paths.get("../config/languages.json"),
                Paths.get("../../config/languages.json"),
                Paths.get("D:/CNPM/Software-technology-project/config/languages.json")
        );

        for (Path p : candidates) {
            if (Files.exists(p)) return p.toFile();
        }
        return null;
    }

    private static List<LanguageDef> parseLanguages(String json) {
        List<LanguageDef> list = new ArrayList<>();
        try {
            JsonNode root = MAPPER.readTree(json);
            JsonNode arr = root.path("languages");
            if (!arr.isArray()) return list;

            for (JsonNode node : arr) {
                String code = node.path("code").asText("");
                if (code.isBlank()) continue;

                String nameVi = node.path("nameVi").asText("");
                String nativeName = node.path("nativeName").asText("");
                String translationLocale = node.path("translationLocale").asText(code);
                String ttsLocale = node.path("ttsLocale").asText(code);
                String direction = node.path("direction").asText("ltr");
                String fontStack = node.path("fontStack").asText("");
                String sentenceDelimiters = node.path("sentenceDelimiters").asText("[.!?;\n。！？；]");
                String clauseDelimiters = node.path("clauseDelimiters").asText("[,，、]");
                double minReadingSpeed = node.path("minReadingSpeed").asDouble(10.0);
                double maxReadingSpeed = node.path("maxReadingSpeed").asDouble(20.0);
                int maxTtsChunkChars = node.path("maxTtsChunkChars").asInt(80);
                int maxTtsChunkBytes = node.path("maxTtsChunkBytes").asInt(120);
                int sortOrder = node.path("sortOrder").asInt(99);
                boolean enabled = node.path("enabled").asBoolean(true);
                String note = node.path("note").asText("");
                String disabledReason = node.path("disabledReason").asText("");

                list.add(new LanguageDef(
                        code, nameVi, nativeName, translationLocale, ttsLocale,
                        direction, fontStack, sentenceDelimiters, clauseDelimiters,
                        minReadingSpeed, maxReadingSpeed, maxTtsChunkChars, maxTtsChunkBytes,
                        sortOrder, enabled, note, disabledReason
                ));
            }
        } catch (Exception e) {
            System.err.println("[LanguageRegistry] Error parsing JSON: " + e.getMessage());
        }
        return list;
    }

    private static List<LanguageDef> getDefaultFallback() {
        return List.of(
                new LanguageDef("vi", "Tiếng Việt", "Tiếng Việt", "vi", "vi-VN", "ltr", "", "[.!?;\n]", "[,]", 12.0, 18.0, 80, 120, 1, true, "", ""),
                new LanguageDef("en", "Tiếng Anh", "English", "en", "en-US", "ltr", "", "[.!?;\n]", "[,]", 14.0, 22.0, 100, 150, 2, true, "", ""),
                new LanguageDef("fr", "Tiếng Pháp", "Français", "fr", "fr-FR", "ltr", "", "[.!?;\n]", "[,]", 14.0, 20.0, 90, 130, 3, true, "", ""),
                new LanguageDef("ja", "Tiếng Nhật", "日本語", "ja", "ja-JP", "ltr", "", "[.!?;\n。！？；]", "[,，、]", 5.0, 10.0, 40, 90, 4, true, "", ""),
                new LanguageDef("zh", "Tiếng Trung (Giản thể)", "中文", "zh-CN", "zh-CN", "ltr", "", "[.!?;\n。！？；]", "[,，、]", 4.0, 8.0, 35, 80, 5, true, "", ""),
                new LanguageDef("ko", "Tiếng Hàn", "한국어", "ko", "ko-KR", "ltr", "", "[.!?;\n]", "[,]", 6.0, 11.0, 50, 100, 6, true, "", "")
        );
    }

    public static List<LanguageDef> getAllLanguages() {
        return allLanguages;
    }

    public static LanguageDef findByCode(String code) {
        if (code == null) return null;
        String clean = code.trim().toLowerCase();
        for (LanguageDef def : allLanguages) {
            if (def.code().equalsIgnoreCase(clean)) {
                return def;
            }
        }
        return null;
    }
}
