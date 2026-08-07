package org.grace.mcp;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class McpOriginFilter extends OncePerRequestFilter {
    private final Set<String> allowedOrigins;

    public McpOriginFilter(@Value("${grace.mcp.allowed-origins:http://localhost:3000}") String allowedOrigins) {
        this.allowedOrigins = Arrays.stream(allowedOrigins.split(",")).map(String::trim).filter(value -> !value.isBlank())
            .map(McpOriginFilter::normalize).collect(Collectors.toUnmodifiableSet());
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/v1/mcp/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
        throws ServletException, IOException {
        String origin = request.getHeader("Origin");
        if (origin != null && !isAllowedOrigin(origin)) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Origin is not allowed for the MCP endpoint");
            return;
        }
        chain.doFilter(request, response);
    }

    boolean isAllowedOrigin(String origin) {
        try { return allowedOrigins.contains(normalize(origin)); }
        catch (RuntimeException ex) { return false; }
    }

    private static String normalize(String value) {
        URI uri = URI.create(value);
        if (uri.getHost() == null || !(uri.getScheme().equals("http") || uri.getScheme().equals("https"))) {
            throw new IllegalArgumentException("MCP origins must be absolute HTTP(S) origins");
        }
        int port = uri.getPort();
        return uri.getScheme().toLowerCase() + "://" + uri.getHost().toLowerCase() + (port < 0 ? "" : ":" + port);
    }
}
