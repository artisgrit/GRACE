package org.grace.security;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class TenantAccessTest {
    private final TenantAccess tenantAccess = new TenantAccess();

    @Test
    void allowsMatchingTenant() {
        assertDoesNotThrow(() -> tenantAccess.require("default", authentication("default", List.of())));
    }

    @Test
    void rejectsDifferentTenant() {
        assertThrows(
            AccessDeniedException.class,
            () -> tenantAccess.require("other", authentication("default", List.of()))
        );
    }

    @Test
    void globalTenantPermissionAllowsCrossTenantAdministration() {
        assertDoesNotThrow(() -> tenantAccess.require(
            "other",
            authentication("default", List.of(new SimpleGrantedAuthority("tenant.manage.global")))
        ));
    }

    private JwtAuthenticationToken authentication(
        String tenant,
        List<SimpleGrantedAuthority> authorities
    ) {
        Instant now = Instant.now();
        Jwt jwt = Jwt.withTokenValue("test-token")
            .header("alg", "HS256")
            .subject("00000000-0000-0000-0000-000000000001")
            .issuedAt(now)
            .expiresAt(now.plusSeconds(60))
            .claim("tenant", tenant)
            .build();
        return new JwtAuthenticationToken(jwt, authorities);
    }
}
