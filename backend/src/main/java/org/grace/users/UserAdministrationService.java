package org.grace.users;

import java.sql.Timestamp;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.grace.configuration.AuditService;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserAdministrationService {
    private final JdbcClient jdbcClient;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    public UserAdministrationService(JdbcClient jdbcClient, PasswordEncoder passwordEncoder, AuditService auditService) {
        this.jdbcClient = jdbcClient;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
    }

    public List<UserSummary> listUsers(String tenantSlug) {
        return jdbcClient.sql("""
            SELECT u.id, u.email, u.display_name, u.enabled, u.email_verified, u.last_login_at, u.created_at
            FROM users u JOIN tenants t ON t.id = u.tenant_id
            WHERE t.slug = :tenantSlug AND u.deleted_at IS NULL
            ORDER BY u.display_name, u.email
            """).param("tenantSlug", tenantSlug).query((rs, rowNum) -> {
                UUID id = rs.getObject("id", UUID.class);
                Timestamp lastLogin = rs.getObject("last_login_at", Timestamp.class);
                return new UserSummary(
                    id,
                    rs.getString("email"),
                    rs.getString("display_name"),
                    rs.getBoolean("enabled"),
                    rs.getBoolean("email_verified"),
                    lastLogin == null ? null : lastLogin.toInstant(),
                    rs.getObject("created_at", Timestamp.class).toInstant(),
                    rolesForUser(id)
                );
            }).list();
    }

    public List<RoleSummary> listRoles(String tenantSlug) {
        return jdbcClient.sql("""
            SELECT r.id, r.name, r.display_name, r.system_role
            FROM roles r JOIN tenants t ON t.id = r.tenant_id
            WHERE t.slug = :tenantSlug ORDER BY r.display_name
            """).param("tenantSlug", tenantSlug).query((rs, rowNum) -> {
                UUID roleId = rs.getObject("id", UUID.class);
                return new RoleSummary(
                    roleId,
                    rs.getString("name"),
                    rs.getString("display_name"),
                    rs.getBoolean("system_role"),
                    permissionsForRole(roleId)
                );
            }).list();
    }

    @Transactional
    public UserSummary createUser(String tenantSlug, CreateUserRequest request) {
        UUID userId = UUID.randomUUID();
        int inserted = jdbcClient.sql("""
            INSERT INTO users (id, tenant_id, email, display_name, password_hash)
            SELECT :id, id, :email, :displayName, :passwordHash FROM tenants
            WHERE slug = :tenantSlug AND enabled = true
            """)
            .param("id", userId)
            .param("email", request.email().trim().toLowerCase())
            .param("displayName", request.displayName().trim())
            .param("passwordHash", passwordEncoder.encode(request.password()))
            .param("tenantSlug", tenantSlug)
            .update();
        if (inserted != 1) {
            throw new IllegalArgumentException("Unknown or disabled tenant: " + tenantSlug);
        }
        assignRoles(tenantSlug, userId, request.roles());
        auditService.record(tenantSlug, "users", "created", "User created: " + request.email(), request.roles());
        return findUser(tenantSlug, userId);
    }

    @Transactional
    public UserSummary updateRoles(String tenantSlug, UUID userId, UpdateUserRolesRequest request) {
        requireUser(tenantSlug, userId);
        jdbcClient.sql("DELETE FROM user_roles WHERE user_id = :userId").param("userId", userId).update();
        assignRoles(tenantSlug, userId, request.roles());
        auditService.record(tenantSlug, "users", "roles_updated", "User roles updated", request.roles());
        return findUser(tenantSlug, userId);
    }

    @Transactional
    public RoleSummary updateRolePermissions(String tenantSlug, UUID roleId, UpdateRolePermissionsRequest request) {
        int roleCount = jdbcClient.sql("""
            SELECT COUNT(*) FROM roles r JOIN tenants t ON t.id = r.tenant_id
            WHERE r.id = :roleId AND t.slug = :tenantSlug
            """).param("roleId", roleId).param("tenantSlug", tenantSlug).query(Integer.class).single();
        if (roleCount != 1) {
            throw new IllegalArgumentException("Unknown role for tenant");
        }
        int knownPermissions = request.permissions().isEmpty() ? 0 : jdbcClient.sql("""
            SELECT COUNT(*) FROM permissions WHERE name IN (:permissions)
            """).param("permissions", request.permissions()).query(Integer.class).single();
        if (knownPermissions != request.permissions().size()) {
            throw new IllegalArgumentException("One or more permissions are unknown");
        }
        jdbcClient.sql("DELETE FROM role_permissions WHERE role_id = :roleId").param("roleId", roleId).update();
        for (String permission : request.permissions()) {
            jdbcClient.sql("""
                INSERT INTO role_permissions (role_id, permission_id)
                SELECT :roleId, id FROM permissions WHERE name = :permission
                """).param("roleId", roleId).param("permission", permission).update();
        }
        auditService.record(tenantSlug, "roles", "permissions_updated", "Role permissions updated", request.permissions());
        return listRoles(tenantSlug).stream().filter(role -> role.id().equals(roleId)).findFirst().orElseThrow();
    }

    private void assignRoles(String tenantSlug, UUID userId, Set<String> roles) {
        int inserted = 0;
        for (String role : roles) {
            inserted += jdbcClient.sql("""
                INSERT INTO user_roles (user_id, role_id)
                SELECT :userId, r.id FROM roles r JOIN tenants t ON t.id = r.tenant_id
                WHERE t.slug = :tenantSlug AND r.name = :role
                """).param("userId", userId).param("tenantSlug", tenantSlug).param("role", role).update();
        }
        if (inserted != roles.size()) {
            throw new IllegalArgumentException("One or more roles are unknown for this tenant");
        }
    }

    private UserSummary findUser(String tenantSlug, UUID userId) {
        return listUsers(tenantSlug).stream().filter(user -> user.id().equals(userId)).findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Unknown user for tenant"));
    }

    private void requireUser(String tenantSlug, UUID userId) {
        findUser(tenantSlug, userId);
    }

    private List<String> rolesForUser(UUID userId) {
        return jdbcClient.sql("""
            SELECT r.name FROM roles r JOIN user_roles ur ON ur.role_id = r.id
            WHERE ur.user_id = :userId ORDER BY r.name
            """).param("userId", userId).query(String.class).list();
    }

    private List<String> permissionsForRole(UUID roleId) {
        return jdbcClient.sql("""
            SELECT p.name FROM role_permissions rp JOIN permissions p ON p.id = rp.permission_id
            WHERE rp.role_id = :roleId ORDER BY p.name
            """).param("roleId", roleId).query(String.class).list();
    }
}
