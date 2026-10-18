package vn.edu.cnpm.gateway.config;

import java.util.concurrent.TimeUnit;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

@Configuration
public class JwksWebClientConfiguration {
    @Bean
    WebClient jwksWebClient(GatewayProperties properties) {
        HttpClient client = HttpClient.create()
                .option(io.netty.channel.ChannelOption.CONNECT_TIMEOUT_MILLIS,
                        Math.toIntExact(properties.getConnectTimeout().toMillis()))
                .responseTimeout(properties.getResponseTimeout());
        return WebClient.builder()
                .baseUrl(properties.getAuthServiceUrl())
                .clientConnector(new ReactorClientHttpConnector(client))
                .build();
    }

    @Bean("contentInternalWebClient")
    WebClient contentInternalWebClient(GatewayProperties properties) {
        return internalClient(properties, properties.getContentServiceUrl());
    }

    @Bean("narrationInternalWebClient")
    WebClient narrationInternalWebClient(GatewayProperties properties) {
        return internalClient(properties, properties.getNarrationServiceUrl());
    }

    private WebClient internalClient(GatewayProperties properties, String baseUrl) {
        HttpClient client = HttpClient.create()
                .option(io.netty.channel.ChannelOption.CONNECT_TIMEOUT_MILLIS,
                        Math.toIntExact(properties.getConnectTimeout().toMillis()))
                .responseTimeout(properties.getResponseTimeout());
        return WebClient.builder().baseUrl(baseUrl)
                .clientConnector(new ReactorClientHttpConnector(client)).build();
    }
}
