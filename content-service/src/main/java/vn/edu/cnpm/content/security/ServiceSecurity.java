package vn.edu.cnpm.content.security;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import vn.edu.cnpm.content.api.ApiException;
import java.util.List;
import java.util.stream.Stream;
@Configuration
public class ServiceSecurity implements WebMvcConfigurer {
    private final List<String> allowedTokens;
    public ServiceSecurity(Environment env) {
        allowedTokens = Stream.of("AUTH_SERVICE_TOKEN","CONTENT_SERVICE_TOKEN","NARRATION_SERVICE_TOKEN","TRANSLATION_SERVICE_TOKEN","TTS_SERVICE_TOKEN","GATEWAY_SERVICE_TOKEN")
                .map(name -> env.getProperty(name, "")).filter(value -> !value.isBlank()).toList();
    }
    @Override public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new InternalTokenInterceptor(allowedTokens)).addPathPatterns("/internal/**");
        registry.addInterceptor(new AdminRoleInterceptor()).addPathPatterns("/api/v1/**").excludePathPatterns("/api/v1/client/**");
    }
    static class InternalTokenInterceptor implements HandlerInterceptor {
        private final List<String> tokens;
        InternalTokenInterceptor(List<String> tokens) { this.tokens=tokens; }
        @Override public boolean preHandle(HttpServletRequest request,HttpServletResponse response,Object handler) {
            String value=request.getHeader("X-Service-Token");
            if(value==null || tokens.stream().noneMatch(token -> java.security.MessageDigest.isEqual(token.getBytes(),value.getBytes())))
                throw new ApiException(HttpStatus.UNAUTHORIZED,"UNAUTHORIZED","Thiếu hoặc sai X-Service-Token");
            return true;
        }
    }
    static class AdminRoleInterceptor implements HandlerInterceptor {
        @Override public boolean preHandle(HttpServletRequest request,HttpServletResponse response,Object handler) {
            String role = request.getHeader("X-User-Role");
            if(!"CONTENT_ADMIN".equals(role) && !"SUPER_ADMIN".equals(role))
                throw new ApiException(HttpStatus.FORBIDDEN,"FORBIDDEN","Không đủ quyền truy cập");
            return true;
        }
    }
}
