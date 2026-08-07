package org.grace.quality;

import java.util.List;import java.util.Map;import java.util.UUID;
import org.grace.security.TenantAccess;import org.springframework.security.access.prepost.PreAuthorize;import org.springframework.security.core.Authentication;import org.springframework.security.oauth2.jwt.Jwt;import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1/tenants/{tenant}")
public class ContentQualityController {
    private final ContentQualityService quality;private final TenantAccess tenants;
    public ContentQualityController(ContentQualityService quality,TenantAccess tenants){this.quality=quality;this.tenants=tenants;}
    @GetMapping("/quality/guides") @PreAuthorize("hasAuthority('quality.manage')") public List<Map<String,Object>> assessments(@PathVariable String tenant,Authentication auth){tenants.require(tenant,auth);return quality.assessments(tenant);}
    @PostMapping("/quality/run") @PreAuthorize("hasAuthority('quality.manage')") public Map<String,Object> run(@PathVariable String tenant,Authentication auth){tenants.require(tenant,auth);return quality.run(tenant);}
    @GetMapping("/notifications") public List<Map<String,Object>> notifications(@PathVariable String tenant,Authentication auth){tenants.require(tenant,auth);Jwt jwt=(Jwt)auth.getPrincipal();return quality.notifications(tenant,UUID.fromString(jwt.getSubject()));}
}
