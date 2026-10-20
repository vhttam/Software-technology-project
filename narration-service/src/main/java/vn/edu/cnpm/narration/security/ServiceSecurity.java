package vn.edu.cnpm.narration.security;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import vn.edu.cnpm.narration.api.ApiException;
import java.util.List;
import java.util.stream.Stream;
@Configuration
public class ServiceSecurity implements WebMvcConfigurer{
    private final List<String> tokens;
    public ServiceSecurity(Environment env){
        tokens=Stream.of("AUTH_SERVICE_TOKEN","CONTENT_SERVICE_TOKEN","NARRATION_SERVICE_TOKEN","TRANSLATION_SERVICE_TOKEN","TTS_SERVICE_TOKEN","GATEWAY_SERVICE_TOKEN")
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
    @Override public void addInterceptors(InterceptorRegistry registry){
        registry.addInterceptor(new InternalGuard(tokens)).addPathPatterns("/internal/**");
        registry.addInterceptor(new AdminGuard()).addPathPatterns("/api/v1/**").excludePathPatterns("/api/v1/client/**");
    }
    private record InternalGuard(List<String> tokens) implements HandlerInterceptor{
        @Override public boolean preHandle(HttpServletRequest req,HttpServletResponse res,Object handler){
            String token=req.getHeader("X-Service-Token");
            if(token==null||tokens.stream().noneMatch(t->java.security.MessageDigest.isEqual(t.getBytes(),token.getBytes())))
                throw new ApiException(HttpStatus.UNAUTHORIZED,"INVALID_CREDENTIALS","Thiếu hoặc sai X-Service-Token");
            return true;
        }
    }
    private static class AdminGuard implements HandlerInterceptor{
        @Override public boolean preHandle(HttpServletRequest req,HttpServletResponse res,Object handler){
            String role = req.getHeader("X-User-Role");
            if(!"CONTENT_ADMIN".equals(role) && !"SUPER_ADMIN".equals(role))
                throw new ApiException(HttpStatus.FORBIDDEN,"INVALID_CREDENTIALS","Không đủ quyền truy cập");
            return true;
        }
    }
}
