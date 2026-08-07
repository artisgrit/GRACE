package org.grace.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RateLimitFilterTest {
    @Test
    void blocksLoginAtConfiguredBoundaryAndAddsRetryMetadata() throws Exception {
        RateLimitFilter filter = filter(2, 5, 5);

        assertThat(invoke(filter, "/api/v1/auth/login").getStatus()).isEqualTo(200);
        assertThat(invoke(filter, "/api/v1/auth/login").getStatus()).isEqualTo(200);
        MockHttpServletResponse blocked = invoke(filter, "/api/v1/auth/login");

        assertThat(blocked.getStatus()).isEqualTo(429);
        assertThat(blocked.getHeader("Retry-After")).isEqualTo("60");
        assertThat(blocked.getContentType()).isEqualTo("application/problem+json");
    }

    @Test
    void doesNotLimitUnclassifiedOrPreflightRequests() throws Exception {
        RateLimitFilter filter = filter(1, 1, 1);

        assertThat(invoke(filter, "/api/v1/admin").getHeader("X-RateLimit-Limit")).isNull();
        MockHttpServletRequest preflight = request("/api/v1/public/tenants/default/guides");
        preflight.setMethod("OPTIONS");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(preflight, response, new MockFilterChain());
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @SuppressWarnings("unchecked")
    private static RateLimitFilter filter(int login, int mcp, int publicLimit) {
        ObjectProvider<StringRedisTemplate> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(null);
        return new RateLimitFilter(provider, new SimpleMeterRegistry(), true, login, mcp, publicLimit);
    }

    private static MockHttpServletResponse invoke(RateLimitFilter filter, String path) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request(path), response, new MockFilterChain());
        return response;
    }

    private static MockHttpServletRequest request(String path) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
        request.setRemoteAddr("192.0.2.10");
        return request;
    }
}
