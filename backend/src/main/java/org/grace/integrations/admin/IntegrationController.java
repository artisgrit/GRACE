package org.grace.integrations.admin;

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
public class IntegrationController {
    private final IntegrationService integrationService;
    private final TenantAccess tenantAccess;

    public IntegrationController(IntegrationService integrationService, TenantAccess tenantAccess) {
        this.integrationService = integrationService;
        this.tenantAccess = tenantAccess;
    }

    @GetMapping("/api/v1/integration-catalogue")
    public List<ConnectorCatalogueItem> catalogue() {
        return integrationService.catalogue();
    }

    @GetMapping("/api/v1/tenants/{tenantSlug}/integrations")
    @PreAuthorize("hasAuthority('integration.manage')")
    public List<IntegrationConfiguration> list(@PathVariable String tenantSlug, Authentication authentication) {
        tenantAccess.require(tenantSlug, authentication);
        return integrationService.list(tenantSlug);
    }

    @PutMapping("/api/v1/tenants/{tenantSlug}/integrations")
    @PreAuthorize("hasAuthority('integration.manage')")
    public IntegrationConfiguration save(
        @PathVariable String tenantSlug,
        Authentication authentication,
        @Valid @RequestBody IntegrationRequest request
    ) {
        tenantAccess.require(tenantSlug, authentication);
        return integrationService.save(tenantSlug, request);
    }
}
