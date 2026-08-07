package org.grace.guides;

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
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/tenants/{tenantSlug}")
public class GuideController {
    private final GuideService guideService;
    private final TenantAccess tenantAccess;

    public GuideController(GuideService guideService, TenantAccess tenantAccess) {
        this.guideService = guideService;
        this.tenantAccess = tenantAccess;
    }

    @GetMapping("/guide-types")
    @PreAuthorize("hasAuthority('guide.create')")
    public List<GuideType> types(@PathVariable String tenantSlug, Authentication authentication) {
        tenantAccess.require(tenantSlug, authentication);
        return guideService.listTypes(tenantSlug);
    }

    @GetMapping("/guide-templates")
    @PreAuthorize("hasAuthority('guide.create')")
    public List<GuideTemplate> templates(@PathVariable String tenantSlug, Authentication authentication) {
        tenantAccess.require(tenantSlug, authentication);
        return guideService.listTemplates(tenantSlug);
    }

    @GetMapping("/guides")
    @PreAuthorize("hasAuthority('guide.edit')")
    public List<GuideSummary> list(
        @PathVariable String tenantSlug,
        Authentication authentication,
        @AuthenticationPrincipal Jwt jwt
    ) {
        tenantAccess.require(tenantSlug, authentication);
        return guideService.list(tenantSlug, userId(jwt), canManageAll(authentication));
    }

    @PostMapping("/guides")
    @PreAuthorize("hasAuthority('guide.create')")
    public GuideDetail create(
        @PathVariable String tenantSlug,
        Authentication authentication,
        @AuthenticationPrincipal Jwt jwt,
        @Valid @RequestBody CreateGuideRequest request
    ) {
        tenantAccess.require(tenantSlug, authentication);
        return guideService.create(tenantSlug, userId(jwt), request);
    }

    @GetMapping("/guides/{guideId}")
    @PreAuthorize("hasAuthority('guide.edit')")
    public GuideDetail get(
        @PathVariable String tenantSlug,
        @PathVariable UUID guideId,
        Authentication authentication,
        @AuthenticationPrincipal Jwt jwt
    ) {
        requireEditable(tenantSlug, guideId, authentication, jwt);
        return guideService.get(tenantSlug, guideId);
    }

    @PutMapping("/guides/{guideId}")
    @PreAuthorize("hasAuthority('guide.edit')")
    public GuideDetail update(
        @PathVariable String tenantSlug,
        @PathVariable UUID guideId,
        Authentication authentication,
        @AuthenticationPrincipal Jwt jwt,
        @Valid @RequestBody UpdateGuideRequest request
    ) {
        requireEditable(tenantSlug, guideId, authentication, jwt);
        return guideService.update(tenantSlug, guideId, userId(jwt), request);
    }

    @GetMapping("/guides/{guideId}/revisions")
    @PreAuthorize("hasAuthority('guide.edit')")
    public List<GuideRevision> revisions(
        @PathVariable String tenantSlug,
        @PathVariable UUID guideId,
        Authentication authentication,
        @AuthenticationPrincipal Jwt jwt
    ) {
        requireEditable(tenantSlug, guideId, authentication, jwt);
        return guideService.revisions(tenantSlug, guideId);
    }

    @PostMapping("/guides/{guideId}/revisions/{revisionNumber}/restore")
    @PreAuthorize("hasAuthority('guide.edit')")
    public GuideDetail restore(
        @PathVariable String tenantSlug,
        @PathVariable UUID guideId,
        @PathVariable int revisionNumber,
        Authentication authentication,
        @AuthenticationPrincipal Jwt jwt
    ) {
        requireEditable(tenantSlug, guideId, authentication, jwt);
        return guideService.restore(tenantSlug, guideId, revisionNumber, userId(jwt));
    }

    private void requireEditable(String tenantSlug, UUID guideId, Authentication authentication, Jwt jwt) {
        tenantAccess.require(tenantSlug, authentication);
        if (!guideService.canEdit(tenantSlug, guideId, userId(jwt), canManageAll(authentication))) {
            throw new AccessDeniedException("You are not an owner or editor of this guide");
        }
    }

    private boolean canManageAll(Authentication authentication) {
        return authentication.getAuthorities().stream().anyMatch(authority ->
            authority.getAuthority().equals("guide.publish") || authority.getAuthority().equals("guide.review")
        );
    }

    private UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
