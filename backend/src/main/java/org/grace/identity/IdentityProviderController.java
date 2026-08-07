package org.grace.identity;

import jakarta.validation.Valid;
import java.util.List;
import org.grace.security.TenantAccess;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/tenants/{tenantSlug}/identity-providers")
public class IdentityProviderController {
    private final IdentityProviderService identityProviderService;
    private final TenantAccess tenantAccess;

    public IdentityProviderController(IdentityProviderService identityProviderService, TenantAccess tenantAccess) {
        this.identityProviderService = identityProviderService;
        this.tenantAccess = tenantAccess;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('identity.manage')")
    public List<IdentityProviderConfiguration> list(@PathVariable String tenantSlug, Authentication authentication) {
        tenantAccess.require(tenantSlug, authentication);
        return identityProviderService.list(tenantSlug);
    }

    @PutMapping
    @PreAuthorize("hasAuthority('identity.manage')")
    public IdentityProviderConfiguration save(
        @PathVariable String tenantSlug,
        Authentication authentication,
        @Valid @RequestBody IdentityProviderRequest request
    ) {
        tenantAccess.require(tenantSlug, authentication);
        return identityProviderService.save(tenantSlug, request);
    }
}
