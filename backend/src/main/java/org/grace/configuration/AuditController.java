package org.grace.configuration;

import java.util.List;
import org.grace.security.TenantAccess;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/tenants/{tenantSlug}/audit-events")
public class AuditController {
    private final AuditService auditService;
    private final TenantAccess tenantAccess;

    public AuditController(AuditService auditService, TenantAccess tenantAccess) {
        this.auditService = auditService;
        this.tenantAccess = tenantAccess;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('security.audit.view')")
    public List<AuditEvent> recent(@PathVariable String tenantSlug, Authentication authentication) {
        tenantAccess.require(tenantSlug, authentication);
        return auditService.recent(tenantSlug);
    }
}
