package vn.edu.cnpm.tts.storage;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
@Component
public class SignedUrlService {
    private final byte[] secret;private final String publicBaseUrl;private final Clock clock;
    public SignedUrlService(Environment env,Clock clock) {
        this.clock=clock;this.publicBaseUrl=env.getProperty("OBJECT_STORAGE_PUBLIC_URL","http://localhost:8085");
        String configured=env.getProperty("STORAGE_SIGNING_SECRET","");
        if(configured.isBlank()) { secret=new byte[32];new SecureRandom().nextBytes(secret); }
        else secret=configured.getBytes(StandardCharsets.UTF_8);
    }
    public String createUrl(String objectKey,Instant expiresAt) {
        long expires=expiresAt.getEpochSecond();String signature=sign(objectKey,expires);
        return publicBaseUrl+"/media/"+objectKey+"?expires="+expires+"&signature="+signature;
    }
    public boolean isValid(String objectKey,long expires,String signature) {
        if(expires<=clock.instant().getEpochSecond())return false;
        return MessageDigest.isEqual(sign(objectKey,expires).getBytes(StandardCharsets.UTF_8),
                (signature==null?"":signature).getBytes(StandardCharsets.UTF_8));
    }
    public Instant expiresAt() { return clock.instant().plus(Duration.ofHours(1)); }
    private String sign(String objectKey,long expires) {
        try {
            Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(secret,"HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal((objectKey+":"+expires).getBytes(StandardCharsets.UTF_8)));
        } catch(Exception ex) { throw new IllegalStateException("Không thể ký signed URL",ex); }
    }
}
