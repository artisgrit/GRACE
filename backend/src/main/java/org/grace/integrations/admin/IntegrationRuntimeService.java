package org.grace.integrations.admin;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.InetAddress;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.grace.configuration.JdbcJson;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

@Service
public class IntegrationRuntimeService {
    private final JdbcClient jdbc;
    private final ObjectMapper mapper;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(4)).followRedirects(HttpClient.Redirect.NEVER).build();

    public IntegrationRuntimeService(JdbcClient jdbc, ObjectMapper mapper) { this.jdbc = jdbc; this.mapper = mapper; }

    public Map<String, Object> execute(String tenant, String connectorKey, String operation) {
        UUID runId = UUID.randomUUID();
        jdbc.sql("INSERT INTO integration_runs (id,tenant_slug,connector_key,operation,status) VALUES (:id,:tenant,:key,:op,'RUNNING')")
            .param("id", runId).param("tenant", tenant).param("key", connectorKey).param("op", operation).update();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("runId", runId); result.put("connectorKey", connectorKey); result.put("operation", operation);
        try {
            IntegrationConfiguration integration = configuration(tenant, connectorKey);
            Map<String, Object> config = mapper.convertValue(integration.configuration(), new TypeReference<>() {});
            if (!integration.enabled()) throw new IllegalStateException("Connector is disabled");
            URI target = target(connectorKey, config, operation);
            HttpRequest.Builder request = HttpRequest.newBuilder(target).timeout(Duration.ofSeconds(12)).GET()
                .header("Accept", connectorKey.equals("oai-pmh") ? "application/xml,text/xml" : "application/json,application/xml,text/xml,*/*");
            String token = string(config, "apiToken");
            if (token != null) request.header("Authorization", token.toLowerCase(Locale.ROOT).startsWith("bearer ") ? token : "Bearer " + token);
            HttpResponse<String> response = http.send(request.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 300) throw new IllegalStateException("Remote system returned HTTP " + response.statusCode());
            int records = operation.equals("SYNC") ? recordCount(connectorKey, response.body()) : 0;
            result.put("status", "SUCCEEDED"); result.put("httpStatus", response.statusCode()); result.put("recordsProcessed", records);
            complete(runId, tenant, connectorKey, "SUCCEEDED", records, result, null);
        } catch (Exception ex) {
            String error = concise(ex);
            result.put("status", "FAILED"); result.put("recordsProcessed", 0); result.put("error", error);
            complete(runId, tenant, connectorKey, "FAILED", 0, result, error);
        }
        return result;
    }

    public Map<String, Object> validateLti(String tenant, String connectorKey) {
        UUID runId = UUID.randomUUID();
        try {
            IntegrationConfiguration integration = configuration(tenant, connectorKey);
            Map<String, Object> config = mapper.convertValue(integration.configuration(), new TypeReference<>() {});
            List<String> missing = List.of("issuer", "clientId", "deploymentId").stream().filter(key -> string(config, key) == null).toList();
            Map<String, Object> result = Map.of("runId", runId, "connectorKey", connectorKey, "operation", "LTI_VALIDATE",
                "status", missing.isEmpty() ? "SUCCEEDED" : "FAILED", "missing", missing,
                "message", missing.isEmpty() ? "LTI 1.3 platform registration is structurally valid" : "Required registration fields are missing");
            jdbc.sql("INSERT INTO integration_runs (id,tenant_slug,connector_key,operation,status,details,completed_at,error_message) VALUES (:id,:tenant,:key,'LTI_VALIDATE',:status,CAST(:details AS jsonb),CURRENT_TIMESTAMP,:error)")
                .param("id", runId).param("tenant", tenant).param("key", connectorKey).param("status", result.get("status"))
                .param("details", JdbcJson.write(result, mapper)).param("error", missing.isEmpty() ? null : "Missing required LTI fields").update();
            updateHealth(tenant, connectorKey, result);
            return result;
        } catch (Exception ex) {
            return Map.of("runId", runId, "connectorKey", connectorKey, "operation", "LTI_VALIDATE", "status", "FAILED", "error", concise(ex));
        }
    }

    public List<Map<String, Object>> runs(String tenant) {
        return jdbc.sql("""
            SELECT id,connector_key,operation,status,records_processed,details,started_at,completed_at,error_message
            FROM integration_runs WHERE tenant_slug=:tenant ORDER BY started_at DESC LIMIT 50
            """).param("tenant", tenant).query((rs, n) -> {
                Map<String,Object> row = new LinkedHashMap<>(); row.put("id", rs.getObject("id"));
                row.put("connectorKey", rs.getString("connector_key")); row.put("operation", rs.getString("operation"));
                row.put("status", rs.getString("status")); row.put("recordsProcessed", rs.getInt("records_processed"));
                row.put("details", JdbcJson.read(rs, "details", mapper)); row.put("startedAt", rs.getTimestamp("started_at").toInstant());
                row.put("completedAt", rs.getTimestamp("completed_at") == null ? null : rs.getTimestamp("completed_at").toInstant());
                row.put("error", rs.getString("error_message")); return row;
            }).list();
    }

    private IntegrationConfiguration configuration(String tenant, String key) {
        return jdbc.sql("""
            SELECT id,tenant_slug,connector_key,connector_type,display_name,enabled,configuration_schema,configuration,mapping_profile,schedule,health,updated_at
            FROM integration_configurations WHERE tenant_slug=:tenant AND connector_key=:key
            """).param("tenant", tenant).param("key", key).query((rs, n) -> new IntegrationConfiguration(
                rs.getObject("id", UUID.class), rs.getString("tenant_slug"), rs.getString("connector_key"), rs.getString("connector_type"),
                rs.getString("display_name"), rs.getBoolean("enabled"), JdbcJson.read(rs,"configuration_schema",mapper),
                JdbcJson.read(rs,"configuration",mapper), JdbcJson.read(rs,"mapping_profile",mapper), JdbcJson.read(rs,"schedule",mapper),
                JdbcJson.read(rs,"health",mapper), rs.getTimestamp("updated_at").toInstant())).optional()
            .orElseThrow(() -> new IllegalArgumentException("Unknown connector: " + key));
    }

    private URI target(String key, Map<String,Object> config, String operation) {
        String base = required(config, "baseUrl").replaceAll("/+$", "");
        URI uri = URI.create(base);
        if (!(uri.getScheme().equals("https") || uri.getScheme().equals("http")) || uri.getHost() == null) throw new IllegalArgumentException("baseUrl must be an absolute HTTP(S) URL");
        validateDestination(uri, Boolean.TRUE.equals(config.get("allowPrivateNetwork")));
        return switch (key.toLowerCase(Locale.ROOT)) {
            case "dspace" -> URI.create(base + (operation.equals("SYNC") ? "/server/api/core/items?size=20" : "/server/api"));
            case "oai-pmh" -> URI.create(base + (base.contains("?") ? "&" : "?") + "verb=" + (operation.equals("SYNC") ? "ListRecords&metadataPrefix=" + encode(string(config,"metadataPrefix") == null ? "oai_dc" : string(config,"metadataPrefix")) : "Identify"));
            case "vufind" -> URI.create(base + "/api/v1/search?lookfor=grace&type=AllFields&limit=1");
            case "koha" -> URI.create(base + "/api/v1/biblios?_per_page=" + (operation.equals("SYNC") ? "20" : "1"));
            case "ojs" -> URI.create(base + "/api/v1/contexts");
            default -> throw new IllegalArgumentException("No HTTP runtime profile for connector: " + key);
        };
    }

    private static void validateDestination(URI uri, boolean allowPrivateNetwork) {
        if (allowPrivateNetwork) return;
        try {
            for (InetAddress address : InetAddress.getAllByName(uri.getHost())) {
                if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress() || address.isSiteLocalAddress()) {
                    throw new IllegalArgumentException("Private network destinations require allowPrivateNetwork=true");
                }
            }
        } catch (java.net.UnknownHostException ex) {
            throw new IllegalArgumentException("Connector host could not be resolved");
        }
    }

    private int recordCount(String key, String body) {
        try {
            if (key.equalsIgnoreCase("oai-pmh")) return Math.max(0, body.split("<record(?:\\s|>)", -1).length - 1);
            JsonNode node = mapper.readTree(body);
            if (node.isArray()) return node.size();
            for (String field : List.of("items", "records", "results", "biblios")) if (node.path(field).isArray()) return node.path(field).size();
        } catch (Exception ignored) { }
        return 0;
    }

    private void complete(UUID id, String tenant, String key, String status, int records, Map<String,Object> details, String error) {
        jdbc.sql("UPDATE integration_runs SET status=:status,records_processed=:records,details=CAST(:details AS jsonb),completed_at=CURRENT_TIMESTAMP,error_message=:error WHERE id=:id")
            .param("status",status).param("records",records).param("details",JdbcJson.write(details,mapper)).param("error",error).param("id",id).update();
        updateHealth(tenant,key,details);
    }
    private void updateHealth(String tenant, String key, Map<String,Object> health) { jdbc.sql("UPDATE integration_configurations SET health=CAST(:health AS jsonb),updated_at=CURRENT_TIMESTAMP WHERE tenant_slug=:tenant AND connector_key=:key")
        .param("health",JdbcJson.write(health,mapper)).param("tenant",tenant).param("key",key).update(); }
    private static String required(Map<String,Object> config, String key) { String value=string(config,key); if(value==null) throw new IllegalArgumentException(key+" is required"); return value; }
    private static String string(Map<String,Object> config, String key) { Object value=config.get(key); return value==null || value.toString().isBlank() ? null : value.toString().trim(); }
    private static String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
    private static String concise(Exception ex) { String value=ex.getMessage()==null?ex.getClass().getSimpleName():ex.getMessage(); return value.substring(0,Math.min(value.length(),1000)); }
}
