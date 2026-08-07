package org.grace.analytics;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.grace.configuration.JdbcJson;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

@Service
public class AnalyticsService {
    private final JdbcClient jdbc;
    private final ObjectMapper mapper;
    public AnalyticsService(JdbcClient jdbc, ObjectMapper mapper) { this.jdbc=jdbc; this.mapper=mapper; }

    public void record(String tenant, AnalyticsEventRequest request) {
        Map<String,Object> metadata = request.metadata() == null ? Map.of() : request.metadata();
        String encoded = JdbcJson.write(metadata, mapper);
        if (encoded.length() > 8000) throw new IllegalArgumentException("Analytics metadata is too large");
        jdbc.sql("""
            INSERT INTO analytics_events (tenant_slug,event_type,entity_type,entity_id,path,session_hash,metadata)
            SELECT :tenant,:eventType,:entityType,:entityId,:path,:sessionHash,CAST(:metadata AS jsonb)
            WHERE EXISTS (SELECT 1 FROM tenants WHERE slug=:tenant AND enabled=true)
            """).param("tenant",tenant).param("eventType",request.eventType()).param("entityType",trim(request.entityType()))
            .param("entityId",request.entityId()).param("path",trim(request.path())).param("sessionHash",trim(request.sessionHash()))
            .param("metadata",encoded).update();
    }

    public Map<String,Object> summary(String tenant, int days) {
        int window = Math.max(1, Math.min(days, 365));
        Map<String,Long> totals = new LinkedHashMap<>();
        jdbc.sql("""
            SELECT event_type,count(*) total FROM analytics_events
            WHERE tenant_slug=:tenant AND occurred_at >= CURRENT_TIMESTAMP - (:days * interval '1 day')
            GROUP BY event_type ORDER BY event_type
            """).param("tenant",tenant).param("days",window)
            .query((rs,n) -> Map.entry(rs.getString("event_type"),rs.getLong("total"))).list()
            .forEach(entry -> totals.put(entry.getKey(),entry.getValue()));
        List<Map<String,Object>> popular = jdbc.sql("""
            SELECT path,count(*) views FROM analytics_events
            WHERE tenant_slug=:tenant AND event_type IN ('PAGE_VIEW','GUIDE_VIEW') AND path IS NOT NULL
              AND occurred_at >= CURRENT_TIMESTAMP - (:days * interval '1 day')
            GROUP BY path ORDER BY views DESC,path LIMIT 10
            """).param("tenant",tenant).param("days",window).query((rs,n) -> {
                Map<String,Object> row = new LinkedHashMap<>();
                row.put("path", rs.getString("path")); row.put("views", rs.getLong("views")); return row;
            }).list();
        List<Map<String,Object>> trend = jdbc.sql("""
            SELECT occurred_at::date AS event_date, count(*) total FROM analytics_events
            WHERE tenant_slug=:tenant AND occurred_at >= CURRENT_TIMESTAMP - (:days * interval '1 day')
            GROUP BY occurred_at::date ORDER BY event_date
            """).param("tenant",tenant).param("days",window).query((rs,n) -> {
                Map<String,Object> row = new LinkedHashMap<>();
                row.put("day", rs.getDate("event_date").toLocalDate()); row.put("total", rs.getLong("total")); return row;
            }).list();
        return Map.of("days",window,"totals",totals,"popularContent",popular,"trend",trend);
    }
    private static String trim(String value) { return value==null || value.isBlank()?null:value.trim(); }
}
