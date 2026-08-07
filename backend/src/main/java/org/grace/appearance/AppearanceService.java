package org.grace.appearance;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import org.grace.configuration.AuditService;
import org.grace.configuration.JdbcJson;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

@Service
public class AppearanceService {
    private final JdbcClient jdbcClient;
    private final ObjectMapper objectMapper;
    private final AuditService auditService;

    public AppearanceService(JdbcClient jdbcClient, ObjectMapper objectMapper, AuditService auditService) {
        this.jdbcClient = jdbcClient;
        this.objectMapper = objectMapper;
        this.auditService = auditService;
    }

    public AppearanceConfiguration get(String tenantSlug) {
        return jdbcClient.sql("""
            SELECT tenant_slug, schema_version, active_theme_id, brand, tokens, layout,
                   navigation, components, custom_css, updated_at
            FROM appearance_configurations
            WHERE tenant_slug = :tenantSlug
            """)
            .param("tenantSlug", tenantSlug)
            .query((rs, rowNum) -> new AppearanceConfiguration(
                rs.getString("tenant_slug"),
                rs.getInt("schema_version"),
                rs.getString("active_theme_id"),
                JdbcJson.read(rs, "brand", objectMapper),
                JdbcJson.read(rs, "tokens", objectMapper),
                JdbcJson.read(rs, "layout", objectMapper),
                JdbcJson.read(rs, "navigation", objectMapper),
                JdbcJson.read(rs, "components", objectMapper),
                rs.getString("custom_css"),
                rs.getObject("updated_at", Timestamp.class).toInstant()
            ))
            .optional()
            .orElseThrow(() -> new IllegalArgumentException("Unknown tenant appearance configuration: " + tenantSlug));
    }

    public AppearanceConfiguration update(String tenantSlug, AppearanceUpdateRequest request) {
        jdbcClient.sql("""
            INSERT INTO appearance_configurations
                (tenant_slug, schema_version, active_theme_id, brand, tokens, layout, navigation, components, custom_css)
            VALUES
                (:tenantSlug, 1, :activeThemeId, CAST(:brand AS jsonb), CAST(:tokens AS jsonb),
                 CAST(:layout AS jsonb), CAST(:navigation AS jsonb), CAST(:components AS jsonb), :customCss)
            ON CONFLICT (tenant_slug) DO UPDATE SET
                active_theme_id = EXCLUDED.active_theme_id,
                brand = EXCLUDED.brand,
                tokens = EXCLUDED.tokens,
                layout = EXCLUDED.layout,
                navigation = EXCLUDED.navigation,
                components = EXCLUDED.components,
                custom_css = EXCLUDED.custom_css,
                updated_at = CURRENT_TIMESTAMP
            """)
            .param("tenantSlug", tenantSlug)
            .param("activeThemeId", request.activeThemeId())
            .param("brand", JdbcJson.write(request.brand(), objectMapper))
            .param("tokens", JdbcJson.write(request.tokens(), objectMapper))
            .param("layout", JdbcJson.write(request.layout(), objectMapper))
            .param("navigation", JdbcJson.write(request.navigation(), objectMapper))
            .param("components", JdbcJson.write(request.components(), objectMapper))
            .param("customCss", request.customCss())
            .update();

        auditService.record(tenantSlug, "appearance", "updated", "Appearance configuration updated", request.brand());
        return get(tenantSlug);
    }
}
