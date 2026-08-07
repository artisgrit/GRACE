package org.grace.integrations.admin;

import jakarta.validation.Valid;
import java.util.List;
import org.grace.security.TenantAccess;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class IntegrationController {
    private final IntegrationService integrationService;
    private final TenantAccess tenantAccess;
    private final IntegrationRuntimeService runtimeService;

    public IntegrationController(IntegrationService integrationService, TenantAccess tenantAccess, IntegrationRuntimeService runtimeService) {
        this.integrationService = integrationService;
        this.tenantAccess = tenantAccess;
        this.runtimeService = runtimeService;
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

    @GetMapping("/api/v1/tenants/{tenantSlug}/integrations/runs")
    @PreAuthorize("hasAuthority('integration.manage')")
    public List<java.util.Map<String,Object>> runs(@PathVariable String tenantSlug, Authentication authentication) {
        tenantAccess.require(tenantSlug, authentication); return runtimeService.runs(tenantSlug);
    }

    @PostMapping("/api/v1/tenants/{tenantSlug}/integrations/{connectorKey}/test")
    @PreAuthorize("hasAuthority('integration.manage')")
    public java.util.Map<String,Object> test(@PathVariable String tenantSlug, @PathVariable String connectorKey, Authentication authentication) {
        tenantAccess.require(tenantSlug, authentication);
        return connectorKey.equalsIgnoreCase("lti") ? runtimeService.validateLti(tenantSlug, connectorKey) : runtimeService.execute(tenantSlug, connectorKey, "TEST");
    }

    @PostMapping("/api/v1/tenants/{tenantSlug}/integrations/{connectorKey}/sync")
    @PreAuthorize("hasAuthority('integration.manage')")
    public java.util.Map<String,Object> sync(@PathVariable String tenantSlug, @PathVariable String connectorKey, Authentication authentication) {
        tenantAccess.require(tenantSlug, authentication); return runtimeService.execute(tenantSlug, connectorKey, "SYNC");
    }
}
