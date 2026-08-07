package org.grace.appearance;

import jakarta.validation.Valid;
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
@RequestMapping("/api/v1/tenants/{tenantSlug}/appearance")
public class AppearanceController {
    private final AppearanceService appearanceService;
    private final TenantAccess tenantAccess;

    public AppearanceController(AppearanceService appearanceService, TenantAccess tenantAccess) {
        this.appearanceService = appearanceService;
        this.tenantAccess = tenantAccess;
    }

    @GetMapping
    public AppearanceConfiguration get(@PathVariable String tenantSlug) {
        return appearanceService.get(tenantSlug);
    }

    @PutMapping
    @PreAuthorize("hasAuthority('appearance.manage')")
    public AppearanceConfiguration update(
        @PathVariable String tenantSlug,
        Authentication authentication,
        @Valid @RequestBody AppearanceUpdateRequest request
    ) {
        tenantAccess.require(tenantSlug, authentication);
        return appearanceService.update(tenantSlug, request);
    }
}
