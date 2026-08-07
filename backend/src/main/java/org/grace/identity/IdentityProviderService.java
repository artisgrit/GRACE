package org.grace.identity;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;
import org.grace.configuration.AuditService;
import org.grace.configuration.JdbcJson;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

@Service
public class IdentityProviderService {
    private final JdbcClient jdbcClient;
    private final ObjectMapper objectMapper;
    private final AuditService auditService;

    public IdentityProviderService(JdbcClient jdbcClient, ObjectMapper objectMapper, AuditService auditService) {
        this.jdbcClient = jdbcClient;
        this.objectMapper = objectMapper;
        this.auditService = auditService;
    }

    public List<IdentityProviderConfiguration> list(String tenantSlug) {
        return jdbcClient.sql("""
            SELECT id, tenant_slug, provider_key, provider_type, display_name, enabled,
                   default_provider, discovery_rules, attribute_mappings, role_mappings,
                   configuration, updated_at
            FROM identity_provider_configurations
            WHERE tenant_slug = :tenantSlug
            ORDER BY default_provider DESC, display_name
            """)
            .param("tenantSlug", tenantSlug)
            .query((rs, rowNum) -> new IdentityProviderConfiguration(
                rs.getObject("id", UUID.class),
                rs.getString("tenant_slug"),
                rs.getString("provider_key"),
                rs.getString("provider_type"),
                rs.getString("display_name"),
                rs.getBoolean("enabled"),
                rs.getBoolean("default_provider"),
                JdbcJson.read(rs, "discovery_rules", objectMapper),
                JdbcJson.read(rs, "attribute_mappings", objectMapper),
                JdbcJson.read(rs, "role_mappings", objectMapper),
                JdbcJson.read(rs, "configuration", objectMapper),
                rs.getObject("updated_at", Timestamp.class).toInstant()
            ))
            .list();
    }

    public IdentityProviderConfiguration save(String tenantSlug, IdentityProviderRequest request) {
        if (request.defaultProvider()) {
            jdbcClient.sql("""
                UPDATE identity_provider_configurations
                SET default_provider = false, updated_at = CURRENT_TIMESTAMP
                WHERE tenant_slug = :tenantSlug
                """)
                .param("tenantSlug", tenantSlug)
                .update();
        }

        jdbcClient.sql("""
            INSERT INTO identity_provider_configurations
                (tenant_slug, provider_key, provider_type, display_name, enabled, default_provider,
                 discovery_rules, attribute_mappings, role_mappings, configuration)
            VALUES
                (:tenantSlug, :providerKey, :providerType, :displayName, :enabled, :defaultProvider,
                 CAST(:discoveryRules AS jsonb), CAST(:attributeMappings AS jsonb),
                 CAST(:roleMappings AS jsonb), CAST(:configuration AS jsonb))
            ON CONFLICT (tenant_slug, provider_key) DO UPDATE SET
                provider_type = EXCLUDED.provider_type,
                display_name = EXCLUDED.display_name,
                enabled = EXCLUDED.enabled,
                default_provider = EXCLUDED.default_provider,
                discovery_rules = EXCLUDED.discovery_rules,
                attribute_mappings = EXCLUDED.attribute_mappings,
                role_mappings = EXCLUDED.role_mappings,
                configuration = EXCLUDED.configuration,
                updated_at = CURRENT_TIMESTAMP
            """)
            .param("tenantSlug", tenantSlug)
            .param("providerKey", request.providerKey())
            .param("providerType", request.providerType())
            .param("displayName", request.displayName())
            .param("enabled", request.enabled())
            .param("defaultProvider", request.defaultProvider())
            .param("discoveryRules", JdbcJson.write(request.discoveryRules(), objectMapper))
            .param("attributeMappings", JdbcJson.write(request.attributeMappings(), objectMapper))
            .param("roleMappings", JdbcJson.write(request.roleMappings(), objectMapper))
            .param("configuration", JdbcJson.write(request.configuration(), objectMapper))
            .update();

        auditService.record(tenantSlug, "identity", "provider_saved", "Identity provider saved", request.configuration());
        return list(tenantSlug).stream()
            .filter(provider -> provider.providerKey().equals(request.providerKey()))
            .findFirst()
            .orElseThrow();
    }
}
