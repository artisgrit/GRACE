package org.grace.security;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RateLimitFilter extends OncePerRequestFilter {
    private static final Duration WINDOW = Duration.ofMinutes(1);
    private static final int MAX_LOCAL_KEYS = 10_000;

    private final StringRedisTemplate redis;
    private final Map<String, LocalWindow> localWindows = new ConcurrentHashMap<>();
    private final boolean enabled;
    private final int loginLimit;
    private final int mcpLimit;
    private final int publicLimit;
    private final Counter blocked;
    private final Counter fallback;

    public RateLimitFilter(
        ObjectProvider<StringRedisTemplate> redisProvider,
        MeterRegistry meterRegistry,
        @Value("${grace.rate-limit.enabled:true}") boolean enabled,
        @Value("${grace.rate-limit.login-per-minute:10}") int loginLimit,
        @Value("${grace.rate-limit.mcp-per-minute:120}") int mcpLimit,
        @Value("${grace.rate-limit.public-per-minute:240}") int publicLimit
    ) {
        this.redis = redisProvider.getIfAvailable();
        this.enabled = enabled;
        this.loginLimit = positive(loginLimit, "login-per-minute");
        this.mcpLimit = positive(mcpLimit, "mcp-per-minute");
        this.publicLimit = positive(publicLimit, "public-per-minute");
        this.blocked = meterRegistry.counter("grace.rate_limit.blocked");
        this.fallback = meterRegistry.counter("grace.rate_limit.fallback");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
        throws ServletException, IOException {
        Policy policy = policyFor(request.getRequestURI());
        if (!enabled || policy == null || "OPTIONS".equals(request.getMethod())) {
            chain.doFilter(request, response);
            return;
        }

        String key = "grace:rate:" + policy.name() + ":" + hash(request.getRemoteAddr());
        long count = increment(key);
        response.setHeader("X-RateLimit-Limit", Integer.toString(policy.limit()));
        response.setHeader("X-RateLimit-Remaining", Long.toString(Math.max(0, policy.limit() - count)));
        if (count > policy.limit()) {
            blocked.increment();
            response.setStatus(429);
            response.setContentType("application/problem+json");
            response.setHeader("Retry-After", Long.toString(WINDOW.toSeconds()));
            response.getWriter().write("{\"type\":\"about:blank\",\"title\":\"Too Many Requests\",\"status\":429,\"detail\":\"Request limit exceeded. Retry later.\"}");
            return;
        }
        chain.doFilter(request, response);
    }

    Policy policyFor(String path) {
        if ("/api/v1/auth/login".equals(path)) return new Policy("login", loginLimit);
        if (path.startsWith("/api/v1/mcp")) return new Policy("mcp", mcpLimit);
        if (path.startsWith("/api/v1/public/")) return new Policy("public", publicLimit);
        return null;
    }

    private long increment(String key) {
        if (redis != null) {
            try {
                Long count = redis.opsForValue().increment(key);
                if (count != null) {
                    if (count == 1) redis.expire(key, WINDOW);
                    return count;
                }
            } catch (RuntimeException unavailable) {
                fallback.increment();
            }
        }
        return incrementLocally(key);
    }

    private long incrementLocally(String key) {
        Instant now = Instant.now();
        if (localWindows.size() >= MAX_LOCAL_KEYS) {
            localWindows.entrySet().removeIf(entry -> entry.getValue().expiresAt().isBefore(now));
            if (localWindows.size() >= MAX_LOCAL_KEYS) localWindows.clear();
        }
        return localWindows.compute(key, (ignored, current) -> {
            if (current == null || current.expiresAt().isBefore(now)) {
                return new LocalWindow(new AtomicInteger(1), now.plus(WINDOW));
            }
            current.count().incrementAndGet();
            return current;
        }).count().get();
    }

    private static String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static int positive(int value, String name) {
        if (value < 1) throw new IllegalArgumentException("Rate limit " + name + " must be positive");
        return value;
    }

    record Policy(String name, int limit) {}
    private record LocalWindow(AtomicInteger count, Instant expiresAt) {}
}
