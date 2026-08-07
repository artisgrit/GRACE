package org.grace.users;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.grace.security.TenantAccess;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/tenants/{tenantSlug}")
public class UserAdministrationController {
    private final UserAdministrationService service;
    private final TenantAccess tenantAccess;

    public UserAdministrationController(UserAdministrationService service, TenantAccess tenantAccess) {
        this.service = service;
        this.tenantAccess = tenantAccess;
    }

    @GetMapping("/users")
    @PreAuthorize("hasAuthority('user.manage')")
    public List<UserSummary> listUsers(@PathVariable String tenantSlug, Authentication authentication) {
        tenantAccess.require(tenantSlug, authentication);
        return service.listUsers(tenantSlug);
    }

    @PostMapping("/users")
    @PreAuthorize("hasAuthority('user.manage')")
    public UserSummary createUser(
        @PathVariable String tenantSlug,
        Authentication authentication,
        @Valid @RequestBody CreateUserRequest request
    ) {
        tenantAccess.require(tenantSlug, authentication);
        preventGlobalRoleEscalation(request.roles(), authentication);
        return service.createUser(tenantSlug, request);
    }

    @PutMapping("/users/{userId}/roles")
    @PreAuthorize("hasAuthority('role.manage')")
    public UserSummary updateUserRoles(
        @PathVariable String tenantSlug,
        @PathVariable UUID userId,
        Authentication authentication,
        @Valid @RequestBody UpdateUserRolesRequest request
    ) {
        tenantAccess.require(tenantSlug, authentication);
        preventGlobalRoleEscalation(request.roles(), authentication);
        return service.updateRoles(tenantSlug, userId, request);
    }

    @GetMapping("/roles")
    @PreAuthorize("hasAuthority('role.manage')")
    public List<RoleSummary> listRoles(@PathVariable String tenantSlug, Authentication authentication) {
        tenantAccess.require(tenantSlug, authentication);
        return service.listRoles(tenantSlug);
    }

    @PutMapping("/roles/{roleId}/permissions")
    @PreAuthorize("hasAuthority('role.manage')")
    public RoleSummary updateRolePermissions(
        @PathVariable String tenantSlug,
        @PathVariable UUID roleId,
        Authentication authentication,
        @Valid @RequestBody UpdateRolePermissionsRequest request
    ) {
        tenantAccess.require(tenantSlug, authentication);
        boolean globalAdministrator = hasGlobalPermission(authentication);
        RoleSummary targetRole = service.listRoles(tenantSlug).stream()
            .filter(role -> role.id().equals(roleId))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Unknown role for tenant"));
        if (!globalAdministrator && (
            targetRole.name().equals("SUPER_ADMIN") || request.permissions().contains("tenant.manage.global")
        )) {
            throw new AccessDeniedException("Only a global administrator can manage global permissions");
        }
        return service.updateRolePermissions(tenantSlug, roleId, request);
    }

    private void preventGlobalRoleEscalation(java.util.Set<String> roles, Authentication authentication) {
        if (roles.contains("SUPER_ADMIN") && !hasGlobalPermission(authentication)) {
            throw new AccessDeniedException("Only a global administrator can assign SUPER_ADMIN");
        }
    }

    private boolean hasGlobalPermission(Authentication authentication) {
        return authentication.getAuthorities().stream()
            .anyMatch(authority -> authority.getAuthority().equals("tenant.manage.global"));
    }
}
