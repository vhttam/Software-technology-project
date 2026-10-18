import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TestTranslateAndTts {
    private static final HttpClient HTTP = HttpClient.newHttpClient();

    public static void main(String[] args) throws Exception {
        String originalText = "Chùa Một Cột là công trình kiến trúc độc đáo thời Lý tại Hà Nội.";
        System.out.println("Original: " + originalText);

        // 1. Translate vi -> en
        String transUrl = "https://translate.googleapis.com/translate_a/single?client=gtx&sl=auto&tl=en&dt=t&q=" 
                + URLEncoder.encode(originalText, StandardCharsets.UTF_8);
        HttpRequest req = HttpRequest.newBuilder().uri(URI.create(transUrl)).GET().build();
        HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        String translated = extractTranslation(resp.body());
        System.out.println("Extracted translation: " + translated);

        // 2. TTS
        String ttsUrl = "https://translate.google.com/translate_tts?ie=UTF-8&client=tw-ob&tl=en&q=" 
                + URLEncoder.encode(translated, StandardCharsets.UTF_8);
        HttpRequest ttsReq = HttpRequest.newBuilder()
                .uri(URI.create(ttsUrl))
                .header("User-Agent", "Mozilla/5.0")
                .GET()
                .build();
        HttpResponse<byte[]> ttsResp = HTTP.send(ttsReq, HttpResponse.BodyHandlers.ofByteArray());
        System.out.println("TTS Status: " + ttsResp.statusCode() + ", Bytes: " + ttsResp.body().length);
    }

    private static String extractTranslation(String json) {
        Pattern p = Pattern.compile("\\[\\[\\[\"(.*?)\",\"");
        Matcher m = p.matcher(json);
        if (m.find()) {
            return m.group(1).replace("\\\"", "\"");
        }
        return json;
    }
}
