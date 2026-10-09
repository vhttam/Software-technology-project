package vn.sgu.narration.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import vn.sgu.narration.api.ApiError;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Component
public class GatewayRequestAuthenticationFilter extends OncePerRequestFilter {
    private final byte[] expectedToken;
    private final ObjectMapper objectMapper;

    public GatewayRequestAuthenticationFilter(@Value("${app.downstream.internal-token}") String gatewayToken,
                                              ObjectMapper objectMapper) {
        this.expectedToken = gatewayToken.getBytes(StandardCharsets.UTF_8);
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return request.getRequestURI().startsWith("/actuator/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String token = request.getHeader("X-Gateway-Service-Token");
        if (token == null || !MessageDigest.isEqual(expectedToken, token.getBytes(StandardCharsets.UTF_8))) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            String correlationId = request.getHeader("X-Correlation-ID");
            if (correlationId != null && !correlationId.isBlank()) {
                response.setHeader("X-Correlation-ID", correlationId);
            }
            objectMapper.writeValue(response.getOutputStream(), ApiError.of("SERVICE_AUTHENTICATION_FAILED",
                    "Request chỉ được chấp nhận từ API Gateway", null, correlationId));
            return;
        }
        filterChain.doFilter(request, response);
    }
}
