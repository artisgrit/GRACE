package org.grace.resources;

import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/public/tenants/{tenantSlug}")
public class PublicLibraryResourceController {
    private final LibraryResourceService resources;

    public PublicLibraryResourceController(LibraryResourceService resources) { this.resources = resources; }

    @GetMapping("/resources")
    public List<LibraryResource> list(@PathVariable String tenantSlug, @RequestParam(required = false) String type,
                                      @RequestParam(required = false, name = "q") String query) {
        return resources.list(tenantSlug, type, query, true);
    }

    @GetMapping("/resources/{id}")
    public LibraryResource get(@PathVariable String tenantSlug, @PathVariable UUID id) {
        return resources.get(tenantSlug, id, true);
    }

    @GetMapping("/subjects")
    public List<SubjectRecord> subjects(@PathVariable String tenantSlug) {
        return resources.subjects(tenantSlug, true);
    }
}
