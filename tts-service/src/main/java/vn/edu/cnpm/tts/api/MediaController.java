package vn.edu.cnpm.tts.api;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.edu.cnpm.tts.storage.ObjectStorage;
import vn.edu.cnpm.tts.storage.SignedUrlService;
import java.io.IOException;
@RestController
public class MediaController {
    private final ObjectStorage storage;private final SignedUrlService urls;
    public MediaController(ObjectStorage storage,SignedUrlService urls){this.storage=storage;this.urls=urls;}
    @GetMapping("/media/**")
    public ResponseEntity<ByteArrayResource> get(@RequestParam long expires,@RequestParam String signature,HttpServletRequest request) throws IOException {
        String uri=request.getRequestURI();
        String key=uri.substring("/media/".length());
        if(key.isBlank()||!urls.isValid(key,expires,signature))return ResponseEntity.status(403).build();
        try {
            return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL,"private, max-age=3600")
                    .contentType(MediaType.parseMediaType("audio/mpeg"))
                    .body(new ByteArrayResource(storage.get(key)));
        } catch(IOException ex) { return ResponseEntity.notFound().build(); }
    }
}
