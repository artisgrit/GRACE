package org.grace.tenancy;

import java.sql.Timestamp;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TenantService {
    private final JdbcClient jdbcClient;

    public TenantService(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public List<Tenant> list() {
        return jdbcClient.sql("""
            SELECT id, slug, name, primary_domain, timezone, default_locale, enabled, created_at
            FROM tenants ORDER BY name
            """).query((rs, rowNum) -> new Tenant(
                rs.getObject("id", java.util.UUID.class),
                rs.getString("slug"),
                rs.getString("name"),
                rs.getString("primary_domain"),
                rs.getString("timezone"),
                rs.getString("default_locale"),
                rs.getBoolean("enabled"),
                rs.getObject("created_at", Timestamp.class).toInstant()
            )).list();
    }

    @Transactional
    public Tenant create(CreateTenantRequest request) {
        jdbcClient.sql("""
            INSERT INTO tenants (slug, name, primary_domain, timezone, default_locale)
            VALUES (:slug, :name, :primaryDomain, :timezone, :defaultLocale)
            """)
            .param("slug", request.slug())
            .param("name", request.name())
            .param("primaryDomain", request.primaryDomain())
            .param("timezone", request.timezone())
            .param("defaultLocale", request.defaultLocale())
            .update();

        jdbcClient.sql("""
            INSERT INTO roles (tenant_id, name, description, display_name, is_system_role, system_role)
            SELECT target.id, source.name, source.description, source.display_name,
                   source.is_system_role, source.system_role
            FROM tenants target
            JOIN tenants template ON template.slug = 'default'
            JOIN roles source ON source.tenant_id = template.id
            WHERE target.slug = :slug AND source.name <> 'SUPER_ADMIN'
            """).param("slug", request.slug()).update();
        jdbcClient.sql("""
            INSERT INTO role_permissions (role_id, permission_id)
            SELECT target_role.id, rp.permission_id
            FROM tenants target
            JOIN tenants template ON template.slug = 'default'
            JOIN roles source_role ON source_role.tenant_id = template.id
            JOIN role_permissions rp ON rp.role_id = source_role.id
            JOIN roles target_role ON target_role.tenant_id = target.id AND target_role.name = source_role.name
            WHERE target.slug = :slug
            """).param("slug", request.slug()).update();
        jdbcClient.sql("""
            INSERT INTO appearance_configurations
                (tenant_slug, active_theme_id, brand, tokens, layout, navigation, components, custom_css)
            SELECT :slug, active_theme_id,
                   jsonb_set(brand, '{institutionName}', to_jsonb(CAST(:name AS text))),
                   tokens, layout, navigation, components, custom_css
            FROM appearance_configurations WHERE tenant_slug = 'default'
            """).param("slug", request.slug()).param("name", request.name()).update();
        jdbcClient.sql("""
            INSERT INTO guide_templates
                (tenant_id, template_key, name, description, guide_type_id, definition, enabled)
            SELECT target.id, template.template_key, template.name, template.description,
                   template.guide_type_id, template.definition, template.enabled
            FROM tenants target JOIN tenants source ON source.slug = 'default'
            JOIN guide_templates template ON template.tenant_id = source.id
            WHERE target.slug = :slug
            """).param("slug", request.slug()).update();
        jdbcClient.sql("INSERT INTO ai_configurations (tenant_slug) VALUES (:slug)")
            .param("slug", request.slug()).update();
        return find(request.slug());
    }

    public Tenant find(String slug) {
        return list().stream().filter(tenant -> tenant.slug().equals(slug)).findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Unknown tenant: " + slug));
    }
}
