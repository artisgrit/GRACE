package org.grace.workflow;

import jakarta.validation.Valid;
import java.util.UUID;
import org.grace.guides.GuideService;
import org.grace.security.TenantAccess;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/tenants/{tenantSlug}/guides/{guideId}/workflow")
public class WorkflowController {
    private final WorkflowService workflow;
    private final GuideService guides;
    private final TenantAccess tenants;

    public WorkflowController(WorkflowService workflow, GuideService guides, TenantAccess tenants) {
        this.workflow = workflow; this.guides = guides; this.tenants = tenants;
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('guide.edit','guide.review','guide.publish')")
    public WorkflowState state(@PathVariable String tenantSlug, @PathVariable UUID guideId,
                               Authentication auth, @AuthenticationPrincipal Jwt jwt) {
        requireAccess(tenantSlug, guideId, auth, jwt);
        return workflow.state(tenantSlug, guideId);
    }

    @PostMapping("/transitions")
    @PreAuthorize("hasAnyAuthority('guide.edit','guide.review','guide.publish','guide.archive')")
    public WorkflowState transition(@PathVariable String tenantSlug, @PathVariable UUID guideId,
                                    Authentication auth, @AuthenticationPrincipal Jwt jwt,
                                    @Valid @RequestBody WorkflowTransitionRequest request) {
        requireAccess(tenantSlug, guideId, auth, jwt);
        return workflow.transition(tenantSlug, guideId, userId(jwt), auth, request);
    }

    @PostMapping("/comments")
    @PreAuthorize("hasAnyAuthority('guide.edit','guide.review')")
    public WorkflowState comment(@PathVariable String tenantSlug, @PathVariable UUID guideId,
                                 Authentication auth, @AuthenticationPrincipal Jwt jwt,
                                 @Valid @RequestBody ReviewCommentRequest request) {
        requireAccess(tenantSlug, guideId, auth, jwt);
        return workflow.addComment(tenantSlug, guideId, userId(jwt), request);
    }

    @PostMapping("/reviewers")
    @PreAuthorize("hasAuthority('guide.review')")
    public WorkflowState reviewer(@PathVariable String tenantSlug, @PathVariable UUID guideId,
                                  Authentication auth, @AuthenticationPrincipal Jwt jwt,
                                  @Valid @RequestBody ReviewerRequest request) {
        tenants.require(tenantSlug, auth);
        return workflow.assignReviewer(tenantSlug, guideId, userId(jwt), request);
    }

    private void requireAccess(String tenantSlug, UUID guideId, Authentication auth, Jwt jwt) {
        tenants.require(tenantSlug, auth);
        boolean manageAll = auth.getAuthorities().stream().anyMatch(a ->
            a.getAuthority().equals("guide.review") || a.getAuthority().equals("guide.publish"));
        if (!guides.canEdit(tenantSlug, guideId, userId(jwt), manageAll)) {
            throw new AccessDeniedException("You cannot access this guide workflow");
        }
    }

    private UUID userId(Jwt jwt) { return UUID.fromString(jwt.getSubject()); }
}
