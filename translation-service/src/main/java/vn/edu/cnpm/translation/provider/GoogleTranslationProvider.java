package vn.edu.cnpm.translation.provider;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import vn.edu.cnpm.translation.config.LanguageRegistry;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@ConditionalOnProperty(name = "app.translation.provider", havingValue = "real")
public class GoogleTranslationProvider implements TranslationProvider {

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private final String googleTranslateBaseUrl;
    private final String myMemoryBaseUrl;

    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36";

    public GoogleTranslationProvider() {
        this("https://translate.googleapis.com", "https://api.mymemory.translated.net");
    }

    public GoogleTranslationProvider(String googleTranslateBaseUrl, String myMemoryBaseUrl) {
        this.googleTranslateBaseUrl = (googleTranslateBaseUrl != null && !googleTranslateBaseUrl.isBlank())
                ? googleTranslateBaseUrl : "https://translate.googleapis.com";
        this.myMemoryBaseUrl = (myMemoryBaseUrl != null && !myMemoryBaseUrl.isBlank())
                ? myMemoryBaseUrl : "https://api.mymemory.translated.net";
    }

    private static final Object THROTTLE_LOCK = new Object();
    private static long lastCallTimestamp = 0;
    private static final long MIN_CALL_INTERVAL_MS = 300;

    public static long getMinCallIntervalMs() {
        return MIN_CALL_INTERVAL_MS;
    }

    private static void throttle() {
        synchronized (THROTTLE_LOCK) {
            long now = System.currentTimeMillis();
            long wait = (lastCallTimestamp + MIN_CALL_INTERVAL_MS) - now;
            if (wait > 0) {
                try {
                    Thread.sleep(wait);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            lastCallTimestamp = System.currentTimeMillis();
        }
    }

    @Override
    public String translate(String text, String sourceLang, String targetLang) {
        if (text == null || text.isBlank()) return "";
        if (sourceLang != null && sourceLang.trim().equalsIgnoreCase(targetLang != null ? targetLang.trim() : "")) {
            return text;
        }

        String s = (sourceLang == null || sourceLang.isBlank()) ? "vi" : sourceLang.toLowerCase().trim();
        String t = (targetLang == null || targetLang.isBlank()) ? "en" : targetLang.toLowerCase().trim();

        throttle();

        LanguageRegistry.LanguageDef targetDef = LanguageRegistry.findByCode(t);
        String tl = (targetDef != null && targetDef.translationLocale() != null && !targetDef.translationLocale().isBlank())
                ? targetDef.translationLocale() : t;
        LanguageRegistry.LanguageDef srcDef = LanguageRegistry.findByCode(s);
        String sl = (srcDef != null && srcDef.translationLocale() != null && !srcDef.translationLocale().isBlank())
                ? srcDef.translationLocale() : s;

        String encodedText = URLEncoder.encode(text, StandardCharsets.UTF_8);
        String encodedSl = URLEncoder.encode(sl, StandardCharsets.UTF_8);
        String encodedTl = URLEncoder.encode(tl, StandardCharsets.UTF_8);

        // 1. Primary: Google Translate API with client=dict-chrome-ex (highest success rate, clean JSON)
        try {
            String url = googleTranslateBaseUrl + "/translate_a/single?client=dict-chrome-ex&sl="
                    + encodedSl + "&tl=" + encodedTl + "&dt=t&q=" + encodedText;

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("User-Agent", USER_AGENT)
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();

            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() == 200) {
                String parsed = parseGoogleTranslateResponse(resp.body());
                if (parsed != null && !parsed.isBlank() && !parsed.contains("???") && !parsed.equalsIgnoreCase(text)) {
                    return parsed;
                }
            }
        } catch (Exception ex) {
            System.err.println("[GoogleTranslationProvider] Google Translate (dict-chrome-ex) error: " + ex.getMessage());
        }

        // 2. Secondary: clients5 Google Translate endpoint
        try {
            String url = "https://clients5.google.com/translate_a/t?client=dict-chrome-ex&sl="
                    + encodedSl + "&tl=" + encodedTl + "&q=" + encodedText;

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("User-Agent", USER_AGENT)
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();

            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() == 200) {
                String parsed = parseGoogleTranslateResponse(resp.body());
                if (parsed != null && !parsed.isBlank() && !parsed.contains("???") && !parsed.equalsIgnoreCase(text)) {
                    return parsed;
                }
            }
        } catch (Exception ex) {
            System.err.println("[GoogleTranslationProvider] Google Translate (clients5) error: " + ex.getMessage());
        }

        // 3. Tertiary: Google Translate API with client=gtx
        try {
            String url = googleTranslateBaseUrl + "/translate_a/single?client=gtx&sl="
                    + encodedSl + "&tl=" + encodedTl + "&dt=t&q=" + encodedText;

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("User-Agent", USER_AGENT)
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();

            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() == 200) {
                String parsed = parseGoogleTranslateResponse(resp.body());
                if (parsed != null && !parsed.isBlank() && !parsed.contains("???") && !parsed.equalsIgnoreCase(text)) {
                    return parsed;
                }
            }
        } catch (Exception ex) {
            System.err.println("[GoogleTranslationProvider] Google Translate (gtx) error: " + ex.getMessage());
        }

        // 4. Quaternary fallback: MyMemory Translation API
        try {
            String langPair = sl + "|" + tl;
            String url = myMemoryBaseUrl + "/get?q=" + encodedText
                    + "&langpair=" + URLEncoder.encode(langPair, StandardCharsets.UTF_8)
                    + "&de=cnpm_student_test_2026@gmail.com";

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("User-Agent", USER_AGENT)
                    .timeout(Duration.ofSeconds(8))
                    .GET()
                    .build();

            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() == 200) {
                String translated = extractJsonField(resp.body(), "translatedText");
                if (translated != null && !translated.isBlank() && !translated.contains("MYMEMORY WARNING")) {
                    String clean = unescapeUnicode(unescapeHtml(translated));
                    if (!clean.equalsIgnoreCase(text)) {
                        return clean;
                    }
                }
            }
        } catch (Exception ex) {
            System.err.println("[GoogleTranslationProvider] MyMemory translation fallback error: " + ex.getMessage());
        }

        // CRITICAL RULE: When sourceLang != targetLang, NEVER copy raw source text!
        throw new IllegalStateException("Không thể dịch văn bản từ " + s + " sang " + t + " qua các dịch vụ dịch thuật khả dụng.");
    }

    public static String parseGoogleTranslateResponse(String json) {
        if (json == null) return null;
        json = json.trim();

        // Array of single string format: ["translated text"]
        if (json.startsWith("[\"") && json.endsWith("\"]") && !json.startsWith("[[[")) {
            String piece = json.substring(2, json.length() - 2);
            return unescapeJson(piece);
        }

        if (!json.startsWith("[[[")) return json;
        int endOfSegments = json.indexOf("]],");
        if (endOfSegments < 0) endOfSegments = json.length();
        String segmentsBlock = json.substring(3, endOfSegments);

        StringBuilder sb = new StringBuilder();
        int idx = 0;
        while (idx < segmentsBlock.length()) {
            int qStart = segmentsBlock.indexOf("\"", idx);
            if (qStart < 0) break;
            int qEnd = findMatchingQuote(segmentsBlock, qStart + 1);
            if (qEnd < 0) break;
            String piece = segmentsBlock.substring(qStart + 1, qEnd);
            sb.append(unescapeJson(piece));

            int nextSeg = segmentsBlock.indexOf("],[", qEnd);
            if (nextSeg < 0) break;
            idx = nextSeg + 3;
        }
        return sb.length() > 0 ? sb.toString() : json;
    }

    private static int findMatchingQuote(String s, int start) {
        boolean escape = false;
        for (int i = start; i < s.length(); i++) {
            char c = s.charAt(i);
            if (escape) {
                escape = false;
            } else if (c == '\\') {
                escape = true;
            } else if (c == '"') {
                return i;
            }
        }
        return -1;
    }

    private static String unescapeJson(String s) {
        return s.replace("\\\"", "\"").replace("\\n", "\n").replace("\\r", "\r").replace("\\\\", "\\");
    }

    private static String unescapeUnicode(String input) {
        if (input == null) return null;
        StringBuilder b = new StringBuilder();
        int len = input.length();
        for (int i = 0; i < len; i++) {
            char c = input.charAt(i);
            if (c == '\\' && i + 5 < len && input.charAt(i + 1) == 'u') {
                try {
                    int code = Integer.parseInt(input.substring(i + 2, i + 6), 16);
                    b.append((char) code);
                    i += 5;
                    continue;
                } catch (Exception ignored) {}
            }
            b.append(c);
        }
        return b.toString();
    }

    private static String unescapeHtml(String text) {
        return text.replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">");
    }

    private static String extractJsonField(String json, String field) {
        Pattern pattern = Pattern.compile("\"" + field + "\"\\s*:\\s*\"([^\"]*)\"");
        Matcher matcher = pattern.matcher(json);
        if (matcher.find()) return matcher.group(1);
        return null;
    }
}
