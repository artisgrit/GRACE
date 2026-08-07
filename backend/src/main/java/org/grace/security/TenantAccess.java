package org.grace.security;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

@Component
public class TenantAccess {
    public void require(String tenantSlug, Authentication authentication) {
        if (!(authentication.getPrincipal() instanceof Jwt jwt)) {
            throw new AccessDeniedException("Authenticated tenant context is required");
        }
        boolean global = authentication.getAuthorities().stream()
            .anyMatch(authority -> authority.getAuthority().equals("tenant.manage.global"));
        if (!global && !tenantSlug.equals(jwt.getClaimAsString("tenant"))) {
            throw new AccessDeniedException("Access to this tenant is not permitted");
        }
    }
}
