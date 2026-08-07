package org.grace.identity;

import jakarta.validation.Valid;
import java.util.List;
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

    public IdentityProviderController(IdentityProviderService identityProviderService) {
        this.identityProviderService = identityProviderService;
    }

    @GetMapping
    public List<IdentityProviderConfiguration> list(@PathVariable String tenantSlug) {
        return identityProviderService.list(tenantSlug);
    }

    @PutMapping
    public IdentityProviderConfiguration save(
        @PathVariable String tenantSlug,
        @Valid @RequestBody IdentityProviderRequest request
    ) {
        return identityProviderService.save(tenantSlug, request);
    }
}
