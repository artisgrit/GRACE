package org.grace.resources;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.grace.security.TenantAccess;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/tenants/{tenantSlug}")
public class LibraryResourceController {
    private final LibraryResourceService resources;
    private final TenantAccess tenants;

    public LibraryResourceController(LibraryResourceService resources, TenantAccess tenants) {
        this.resources = resources; this.tenants = tenants;
    }

    @GetMapping("/resources")
    @PreAuthorize("hasAnyAuthority('resource.manage','database.manage','staff.manage','faq.manage')")
    public List<LibraryResource> list(@PathVariable String tenantSlug, @RequestParam(required = false) String type,
                                      @RequestParam(required = false, name = "q") String query, Authentication auth) {
        tenants.require(tenantSlug, auth);
        return resources.list(tenantSlug, type, query, false);
    }

    @GetMapping("/resources/{id}")
    @PreAuthorize("hasAnyAuthority('resource.manage','database.manage','staff.manage','faq.manage')")
    public LibraryResource get(@PathVariable String tenantSlug, @PathVariable UUID id, Authentication auth) {
        tenants.require(tenantSlug, auth);
        LibraryResource resource = resources.get(tenantSlug, id, false);
        requireTypePermission(resource.resourceType(), auth);
        return resource;
    }

    @PostMapping("/resources")
    @PreAuthorize("hasAnyAuthority('resource.manage','database.manage','staff.manage','faq.manage')")
    public LibraryResource create(@PathVariable String tenantSlug, Authentication auth,
                                  @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody LibraryResourceRequest request) {
        tenants.require(tenantSlug, auth);
        requireTypePermission(request.resourceType(), auth);
        return resources.create(tenantSlug, UUID.fromString(jwt.getSubject()), request);
    }

    @PutMapping("/resources/{id}")
    @PreAuthorize("hasAnyAuthority('resource.manage','database.manage','staff.manage','faq.manage')")
    public LibraryResource update(@PathVariable String tenantSlug, @PathVariable UUID id, Authentication auth,
                                  @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody LibraryResourceRequest request) {
        tenants.require(tenantSlug, auth);
        requireTypePermission(request.resourceType(), auth);
        LibraryResource existing = resources.get(tenantSlug, id, false);
        requireTypePermission(existing.resourceType(), auth);
        return resources.update(tenantSlug, id, UUID.fromString(jwt.getSubject()), request);
    }

    @GetMapping("/subjects")
    @PreAuthorize("hasAnyAuthority('resource.manage','database.manage','staff.manage','faq.manage','guide.edit')")
    public List<SubjectRecord> subjects(@PathVariable String tenantSlug, Authentication auth) {
        tenants.require(tenantSlug, auth);
        return resources.subjects(tenantSlug, false);
    }

    @PostMapping("/subjects")
    @PreAuthorize("hasAuthority('resource.manage')")
    public SubjectRecord createSubject(@PathVariable String tenantSlug, Authentication auth,
                                       @Valid @RequestBody SubjectRequest request) {
        tenants.require(tenantSlug, auth);
        return resources.createSubject(tenantSlug, request);
    }

    @PutMapping("/subjects/{id}")
    @PreAuthorize("hasAuthority('resource.manage')")
    public SubjectRecord updateSubject(@PathVariable String tenantSlug, @PathVariable UUID id, Authentication auth,
                                       @Valid @RequestBody SubjectRequest request) {
        tenants.require(tenantSlug, auth);
        return resources.updateSubject(tenantSlug, id, request);
    }

    private void requireTypePermission(String type, Authentication auth) {
        String normalized = type.toUpperCase().replace('-', '_');
        String permission = switch (normalized) {
            case "DATABASE" -> "database.manage";
            case "LIBRARIAN" -> "staff.manage";
            case "FAQ" -> "faq.manage";
            case "BOOK", "READING_LIST" -> "resource.manage";
            default -> throw new IllegalArgumentException("Unknown resource type");
        };
        boolean allowed = auth.getAuthorities().stream().anyMatch(a ->
            a.getAuthority().equals(permission) || a.getAuthority().equals("resource.manage"));
        if (!allowed) throw new AccessDeniedException("Missing permission " + permission);
    }
}
