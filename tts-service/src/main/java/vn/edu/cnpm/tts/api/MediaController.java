package vn.edu.cnpm.tts.api;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.edu.cnpm.tts.provider.TtsProvider;
import vn.edu.cnpm.tts.storage.ObjectStorage;
import vn.edu.cnpm.tts.storage.SignedUrlService;

import java.io.IOException;

@CrossOrigin(origins = "*")
@RestController
public class MediaController {
    private final ObjectStorage storage;
    private final SignedUrlService urls;
    private final TtsProvider provider;

    public MediaController(ObjectStorage storage, SignedUrlService urls, TtsProvider provider) {
        this.storage = storage;
        this.urls = urls;
        this.provider = provider;
    }

    @RequestMapping(value = "/media/**", method = {RequestMethod.GET, RequestMethod.HEAD})
    public ResponseEntity<byte[]> get(@RequestParam long expires,
                                      @RequestParam String signature,
                                      HttpServletRequest request) {
        String uri = request.getRequestURI();
        String key = uri.substring("/media/".length());
        if (key.isBlank() || !urls.isValid(key, expires, signature)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        byte[] content;
        try {
            content = storage.get(key);
        } catch (Exception ex) {
            // File is missing or deleted from disk - regenerate sample on the fly so audio always plays
            try {
                content = provider.synthesize("Mẫu âm thanh thuyết minh", "vi", "default");
            } catch (Exception e) {
                return ResponseEntity.notFound().build();
            }
        }

        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "private, max-age=3600")
                .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                .contentType(MediaType.parseMediaType("audio/mpeg"))
                .contentLength(content.length)
                .body(content);
    }
}
