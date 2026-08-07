package org.grace.configuration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

@Service
public class AuditService {
    private final JdbcClient jdbcClient;
    private final ObjectMapper objectMapper;

    public AuditService(JdbcClient jdbcClient, ObjectMapper objectMapper) {
        this.jdbcClient = jdbcClient;
        this.objectMapper = objectMapper;
    }

    public void record(String tenantSlug, String area, String action, String summary, JsonNode payload) {
        jdbcClient.sql("""
            INSERT INTO configuration_audit_events
                (tenant_slug, actor, area, action, summary, payload)
            VALUES (:tenantSlug, :actor, :area, :action, :summary, CAST(:payload AS jsonb))
            """)
            .param("tenantSlug", tenantSlug)
            .param("actor", "system")
            .param("area", area)
            .param("action", action)
            .param("summary", summary)
            .param("payload", JdbcJson.write(payload, objectMapper))
            .update();
    }

    public List<AuditEvent> recent(String tenantSlug) {
        return jdbcClient.sql("""
            SELECT id, tenant_slug, actor, area, action, summary, payload, created_at
            FROM configuration_audit_events
            WHERE tenant_slug = :tenantSlug
            ORDER BY created_at DESC
            LIMIT 50
            """)
            .param("tenantSlug", tenantSlug)
            .query((rs, rowNum) -> new AuditEvent(
                rs.getObject("id", UUID.class),
                rs.getString("tenant_slug"),
                rs.getString("actor"),
                rs.getString("area"),
                rs.getString("action"),
                rs.getString("summary"),
                JdbcJson.read(rs, "payload", objectMapper),
                rs.getObject("created_at", Timestamp.class).toInstant()
            ))
            .list();
    }
}
