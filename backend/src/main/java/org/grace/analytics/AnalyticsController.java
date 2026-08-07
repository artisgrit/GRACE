package org.grace.analytics;

import jakarta.validation.Valid;
import java.util.Map;
import org.grace.security.TenantAccess;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1")
public class AnalyticsController {
    private final AnalyticsService analytics; private final TenantAccess tenants;
    public AnalyticsController(AnalyticsService analytics,TenantAccess tenants){this.analytics=analytics;this.tenants=tenants;}
    @PostMapping("/public/tenants/{tenant}/analytics/events") @ResponseStatus(HttpStatus.ACCEPTED)
    public void record(@PathVariable String tenant,@Valid @RequestBody AnalyticsEventRequest request){analytics.record(tenant,request);}
    @GetMapping("/tenants/{tenant}/analytics/summary") @PreAuthorize("hasAuthority('analytics.view')")
    public Map<String,Object> summary(@PathVariable String tenant,@RequestParam(defaultValue="30") int days,Authentication auth){tenants.require(tenant,auth);return analytics.summary(tenant,days);}
}
