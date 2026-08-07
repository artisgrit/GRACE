package org.grace.integrations.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;
import org.grace.configuration.AuditService;
import org.grace.configuration.JdbcJson;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

@Service
public class IntegrationService {
    private final JdbcClient jdbcClient;
    private final ObjectMapper objectMapper;
    private final AuditService auditService;

    public IntegrationService(JdbcClient jdbcClient, ObjectMapper objectMapper, AuditService auditService) {
        this.jdbcClient = jdbcClient;
        this.objectMapper = objectMapper;
        this.auditService = auditService;
    }

    public List<ConnectorCatalogueItem> catalogue() {
        return List.of(
            new ConnectorCatalogueItem("ILS", "Integrated Library System", List.of("REST", "SRU", "Z39.50", "MARCXML"), List.of("search", "holdings", "availability")),
            new ConnectorCatalogueItem("REPOSITORY", "Institutional Repository", List.of("REST", "OAI-PMH", "ResourceSync", "SWORD"), List.of("harvest", "search", "metadata")),
            new ConnectorCatalogueItem("DISCOVERY", "Discovery System", List.of("REST", "OpenSearch", "Solr"), List.of("federated-search", "deep-links")),
            new ConnectorCatalogueItem("LMS", "Learning Management System", List.of("LTI 1.3", "REST", "OneRoster"), List.of("course-context", "deep-linking")),
            new ConnectorCatalogueItem("IDENTITY", "Identity Provider", List.of("SAML", "OIDC", "LDAP", "SCIM"), List.of("authentication", "provisioning", "role-mapping"))
        );
    }

    public List<IntegrationConfiguration> list(String tenantSlug) {
        return jdbcClient.sql("""
            SELECT id, tenant_slug, connector_key, connector_type, display_name, enabled,
                   configuration_schema, configuration, mapping_profile, schedule, health, updated_at
            FROM integration_configurations
            WHERE tenant_slug = :tenantSlug
            ORDER BY connector_type, display_name
            """)
            .param("tenantSlug", tenantSlug)
            .query((rs, rowNum) -> new IntegrationConfiguration(
                rs.getObject("id", UUID.class),
                rs.getString("tenant_slug"),
                rs.getString("connector_key"),
                rs.getString("connector_type"),
                rs.getString("display_name"),
                rs.getBoolean("enabled"),
                JdbcJson.read(rs, "configuration_schema", objectMapper),
                JdbcJson.read(rs, "configuration", objectMapper),
                JdbcJson.read(rs, "mapping_profile", objectMapper),
                JdbcJson.read(rs, "schedule", objectMapper),
                JdbcJson.read(rs, "health", objectMapper),
                rs.getObject("updated_at", Timestamp.class).toInstant()
            ))
            .list();
    }

    public IntegrationConfiguration save(String tenantSlug, IntegrationRequest request) {
        jdbcClient.sql("""
            INSERT INTO integration_configurations
                (tenant_slug, connector_key, connector_type, display_name, enabled,
                 configuration_schema, configuration, mapping_profile, schedule, health)
            VALUES
                (:tenantSlug, :connectorKey, :connectorType, :displayName, :enabled,
                 CAST(:configurationSchema AS jsonb), CAST(:configuration AS jsonb),
                 CAST(:mappingProfile AS jsonb), CAST(:schedule AS jsonb), CAST(:health AS jsonb))
            ON CONFLICT (tenant_slug, connector_key) DO UPDATE SET
                connector_type = EXCLUDED.connector_type,
                display_name = EXCLUDED.display_name,
                enabled = EXCLUDED.enabled,
                configuration_schema = EXCLUDED.configuration_schema,
                configuration = EXCLUDED.configuration,
                mapping_profile = EXCLUDED.mapping_profile,
                schedule = EXCLUDED.schedule,
                health = EXCLUDED.health,
                updated_at = CURRENT_TIMESTAMP
            """)
            .param("tenantSlug", tenantSlug)
            .param("connectorKey", request.connectorKey())
            .param("connectorType", request.connectorType())
            .param("displayName", request.displayName())
            .param("enabled", request.enabled())
            .param("configurationSchema", JdbcJson.write(request.configurationSchema(), objectMapper))
            .param("configuration", JdbcJson.write(request.configuration(), objectMapper))
            .param("mappingProfile", JdbcJson.write(request.mappingProfile(), objectMapper))
            .param("schedule", JdbcJson.write(request.schedule(), objectMapper))
            .param("health", JdbcJson.write(request.health(), objectMapper))
            .update();

        auditService.record(tenantSlug, "integrations", "connector_saved", "Integration connector saved", request.configuration());
        return list(tenantSlug).stream()
            .filter(integration -> integration.connectorKey().equals(request.connectorKey()))
            .findFirst()
            .orElseThrow();
    }
}
