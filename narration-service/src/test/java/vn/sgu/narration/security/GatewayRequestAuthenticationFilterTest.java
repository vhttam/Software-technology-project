package vn.sgu.narration.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

class GatewayRequestAuthenticationFilterTest {
    private final ExposedFilter filter = new ExposedFilter();

    @Test
    void healthEndpointDoesNotRequireServiceToken() {
        var request = new MockHttpServletRequest("GET", "/health");

        assertThat(filter.shouldSkip(request)).isTrue();
    }

    @Test
    void actuatorHealthRemainsExemptFromServiceToken() {
        var request = new MockHttpServletRequest("GET", "/actuator/health");

        assertThat(filter.shouldSkip(request)).isTrue();
    }

    @Test
    void applicationEndpointsStillRequireServiceToken() {
        var request = new MockHttpServletRequest("GET", "/api/v1/jobs");

        assertThat(filter.shouldSkip(request)).isFalse();
    }

    private static final class ExposedFilter extends GatewayRequestAuthenticationFilter {
        private ExposedFilter() {
            super("expected-token", new ObjectMapper());
        }

        private boolean shouldSkip(MockHttpServletRequest request) {
            return shouldNotFilter(request);
        }
    }
}
