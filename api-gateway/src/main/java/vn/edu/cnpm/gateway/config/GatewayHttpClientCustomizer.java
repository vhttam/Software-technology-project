package vn.edu.cnpm.gateway.config;

import org.springframework.cloud.gateway.config.HttpClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GatewayHttpClientCustomizer {
    @Bean
    HttpClientCustomizer disableAutomaticRetries() {
        return httpClient -> httpClient.disableRetry(true);
    }
}
