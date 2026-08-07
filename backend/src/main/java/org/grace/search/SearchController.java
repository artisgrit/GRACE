package org.grace.search;

import java.util.List;
import java.util.Map;
import org.grace.security.TenantAccess;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class SearchController {
    private final SearchService search;
    private final TenantAccess tenants;
    public SearchController(SearchService search, TenantAccess tenants) { this.search = search; this.tenants = tenants; }

    @GetMapping("/public/tenants/{tenant}/search")
    public SearchResponse search(@PathVariable String tenant, @RequestParam(defaultValue="") String q,
        @RequestParam(required=false) String type, @RequestParam(required=false) String subject,
        @RequestParam(defaultValue="0") int page, @RequestParam(defaultValue="20") int size) {
        return search.search(tenant, q, type, subject, page, size);
    }
    @GetMapping("/public/tenants/{tenant}/search/autocomplete")
    public List<String> autocomplete(@PathVariable String tenant, @RequestParam String q) { return search.autocomplete(tenant, q); }
    @GetMapping("/tenants/{tenant}/search/status") @PreAuthorize("hasAuthority('search.manage')")
    public Map<String,Object> status(@PathVariable String tenant, Authentication auth) { tenants.require(tenant, auth); return search.status(tenant); }
    @PostMapping("/tenants/{tenant}/search/reindex") @PreAuthorize("hasAuthority('search.manage')")
    public Map<String,Object> reindex(@PathVariable String tenant, Authentication auth) { tenants.require(tenant, auth); return search.reindex(tenant); }
}
