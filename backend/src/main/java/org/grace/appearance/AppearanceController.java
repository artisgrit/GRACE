package org.grace.appearance;

import jakarta.validation.Valid;
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

    public AppearanceController(AppearanceService appearanceService) {
        this.appearanceService = appearanceService;
    }

    @GetMapping
    public AppearanceConfiguration get(@PathVariable String tenantSlug) {
        return appearanceService.get(tenantSlug);
    }

    @PutMapping
    public AppearanceConfiguration update(
        @PathVariable String tenantSlug,
        @Valid @RequestBody AppearanceUpdateRequest request
    ) {
        return appearanceService.update(tenantSlug, request);
    }
}
