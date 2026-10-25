package vn.edu.cnpm.translation.security;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import vn.edu.cnpm.translation.api.ApiException;
import java.util.List;
import java.util.stream.Stream;
@Configuration
public class InternalTokenInterceptor implements WebMvcConfigurer {
    private final List<String> tokens;
    public InternalTokenInterceptor(Environment env) {
        tokens=Stream.of("AUTH_SERVICE_TOKEN","CONTENT_SERVICE_TOKEN","NARRATION_SERVICE_TOKEN","TRANSLATION_SERVICE_TOKEN","TTS_SERVICE_TOKEN")
                .map(name->env.getProperty(name, defaultToken(name))).filter(value->!value.isBlank()).toList();
    }
    private static String defaultToken(String name) {
        return switch (name) {
            case "AUTH_SERVICE_TOKEN" -> "auth-token";
            case "CONTENT_SERVICE_TOKEN" -> "content-token";
            case "NARRATION_SERVICE_TOKEN" -> "narration-token";
            case "TRANSLATION_SERVICE_TOKEN" -> "translation-token";
            case "TTS_SERVICE_TOKEN" -> "tts-token";
            case "GATEWAY_SERVICE_TOKEN" -> "gateway-token";
            default -> "";
        };
    }
    @Override public void addInterceptors(InterceptorRegistry registry) { registry.addInterceptor(new Guard(tokens)).addPathPatterns("/internal/**"); }
    private record Guard(List<String> tokens) implements HandlerInterceptor {
        @Override public boolean preHandle(HttpServletRequest request,HttpServletResponse response,Object handler) {
            String value=request.getHeader("X-Service-Token");
            if(value==null||tokens.stream().noneMatch(token->java.security.MessageDigest.isEqual(token.getBytes(),value.getBytes())))
                throw new ApiException(HttpStatus.UNAUTHORIZED,"UNAUTHORIZED","Thiếu hoặc sai X-Service-Token");
            return true;
        }
    }
}
