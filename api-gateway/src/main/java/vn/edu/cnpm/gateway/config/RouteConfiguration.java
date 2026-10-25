package vn.edu.cnpm.gateway.config;

import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;

@Configuration
public class RouteConfiguration {
    @Bean
    RouteLocator gatewayRoutes(RouteLocatorBuilder builder, GatewayProperties properties) {
        RouteLocatorBuilder.Builder routes = builder.routes();
        properties.getRoutes().stream()
                .filter(route -> !"LOCAL".equals(route.getTarget()))
                .forEach(route -> routes.route(route.getId(), predicate -> predicate
                        .path(route.getPath())
                        .and()
                        .method(HttpMethod.valueOf(route.getMethod()))
                        .uri(properties.destination(route.getTarget()))));
        return routes.build();
    }
}
