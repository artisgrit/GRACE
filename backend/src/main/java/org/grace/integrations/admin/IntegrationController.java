package org.grace.integrations.admin;

import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class IntegrationController {
    private final IntegrationService integrationService;

    public IntegrationController(IntegrationService integrationService) {
        this.integrationService = integrationService;
    }

    @GetMapping("/api/v1/integration-catalogue")
    public List<ConnectorCatalogueItem> catalogue() {
        return integrationService.catalogue();
    }

    @GetMapping("/api/v1/tenants/{tenantSlug}/integrations")
    public List<IntegrationConfiguration> list(@PathVariable String tenantSlug) {
        return integrationService.list(tenantSlug);
    }

    @PutMapping("/api/v1/tenants/{tenantSlug}/integrations")
    public IntegrationConfiguration save(
        @PathVariable String tenantSlug,
        @Valid @RequestBody IntegrationRequest request
    ) {
        return integrationService.save(tenantSlug, request);
    }
}
