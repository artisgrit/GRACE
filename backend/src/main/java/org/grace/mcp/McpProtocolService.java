package org.grace.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.grace.guides.GuideService;
import org.grace.resources.LibraryResourceService;
import org.grace.search.SearchService;
import org.grace.semantic.SemanticService;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.RDFFormat;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

@Service
public class McpProtocolService {
    static final String PROTOCOL_VERSION = "2025-11-25";
    private static final Duration SESSION_TTL = Duration.ofMinutes(30);
    private static final String JSON_SCHEMA = "https://json-schema.org/draft/2020-12/schema";
    private final Map<String, Session> sessions = new ConcurrentHashMap<>();
    private final SearchService search;
    private final GuideService guides;
    private final LibraryResourceService resources;
    private final SemanticService semantic;
    private final ObjectMapper mapper;
    private final JdbcClient jdbc;
    private final String audience;

    public McpProtocolService(SearchService search, GuideService guides, LibraryResourceService resources,
                              SemanticService semantic, ObjectMapper mapper, JdbcClient jdbc,
                              @Value("${grace.mcp.audience:grace-mcp}") String audience) {
        this.search=search; this.guides=guides; this.resources=resources; this.semantic=semantic;
        this.mapper=mapper; this.jdbc=jdbc; this.audience=audience;
    }

    McpExchange handle(String tenant, McpRequest request, String sessionId, String protocolHeader, Jwt jwt) {
        if (!"2.0".equals(request.jsonrpc())) return protocolError(request.id(), -32600, "Invalid Request");
        if (request.method().equals("initialize")) return initialize(tenant, request, jwt);
        if (request.method().equals("notifications/initialized")) {
            requireSession(tenant, sessionId, protocolHeader, jwt);
            return new McpExchange(202, null, sessionId);
        }
        requireSession(tenant, sessionId, protocolHeader, jwt);
        return switch (request.method()) {
            case "ping" -> success(request.id(), Map.of(), sessionId);
            case "tools/list" -> success(request.id(), Map.of("tools", tools()), sessionId);
            case "tools/call" -> toolCall(tenant, request, sessionId, jwt);
            case "resources/list" -> success(request.id(), Map.of("resources", resourceList(tenant)), sessionId);
            case "resources/read" -> resourceRead(tenant, request, sessionId, jwt);
            default -> protocolError(request.id(), -32601, "Method not found", sessionId);
        };
    }

    boolean close(String tenant, String sessionId, Jwt jwt) {
        Session session = sessions.get(sessionId);
        if (session == null || !session.tenant().equals(tenant) || !session.subject().equals(jwt.getSubject())) return false;
        sessions.remove(sessionId);
        return true;
    }

    private McpExchange initialize(String tenant, McpRequest request, Jwt jwt) {
        requireAudience(jwt);
        Instant cutoff = Instant.now().minus(SESSION_TTL);
        sessions.entrySet().removeIf(entry -> entry.getValue().startedAt().isBefore(cutoff));
        if (sessions.size() >= 10_000) throw new McpSessionException("MCP session capacity has been reached");
        String sessionId = UUID.randomUUID().toString();
        sessions.put(sessionId, new Session(tenant, jwt.getSubject(), Instant.now()));
        Map<String,Object> result = new LinkedHashMap<>();
        result.put("protocolVersion", PROTOCOL_VERSION);
        result.put("capabilities", Map.of("tools", Map.of("listChanged", false), "resources", Map.of("subscribe", false, "listChanged", false)));
        result.put("serverInfo", Map.of("name", "GRACE Research Discovery", "version", "1.0.0", "description", "Read-only tenant-scoped library research and discovery"));
        result.put("instructions", "Use GRACE tools only for read-only discovery of published institutional library content.");
        return success(request.id(), result, sessionId);
    }

    private McpExchange toolCall(String tenant, McpRequest request, String sessionId, Jwt jwt) {
        JsonNode params = mapper.valueToTree(request.params());
        String name = text(params, "name");
        JsonNode arguments = params == null ? null : params.path("arguments");
        try {
            Object value = switch (name) {
                case "search_library" -> search.search(tenant, required(arguments, "query", 500), optional(arguments, "type"),
                    optional(arguments, "subject"), 0, integer(arguments, "limit", 10, 1, 20));
                case "get_published_guide" -> guides.getPublished(tenant, required(arguments, "slug", 300));
                case "get_library_resource" -> resources.get(tenant, UUID.fromString(required(arguments, "id", 50)), true);
                case "list_subjects" -> resources.subjects(tenant, true);
                case "get_semantic_graph" -> jsonLd(semantic.graph(tenant));
                default -> throw new ToolException("Unknown read-only tool: " + name);
            };
            audit(tenant, jwt, "tools/call", name, "SUCCEEDED");
            return success(request.id(), toolResult(value, false), sessionId);
        } catch (Exception ex) {
            audit(tenant, jwt, "tools/call", name, "REJECTED");
            return success(request.id(), toolResult(Map.of("error", concise(ex)), true), sessionId);
        }
    }

    private McpExchange resourceRead(String tenant, McpRequest request, String sessionId, Jwt jwt) {
        String uri = required(mapper.valueToTree(request.params()), "uri", 500);
        String prefix = "grace://" + tenant + "/";
        if (!uri.startsWith(prefix)) return success(request.id(), toolResult(Map.of("error", "Resource URI is outside the authenticated tenant"), true), sessionId);
        try {
            Object value; String mime="application/json";
            switch (uri.substring(prefix.length())) {
                case "guides" -> value=guides.listPublished(tenant);
                case "subjects" -> value=resources.subjects(tenant, true);
                case "semantic/graph" -> { value=jsonLd(semantic.graph(tenant)); mime="application/ld+json"; }
                default -> throw new ToolException("Unknown GRACE MCP resource");
            }
            String text = value instanceof String string ? string : mapper.writeValueAsString(value);
            audit(tenant, jwt, "resources/read", uri.substring(prefix.length()), "SUCCEEDED");
            return success(request.id(), Map.of("contents", List.of(Map.of("uri", uri, "mimeType", mime, "text", text))), sessionId);
        } catch(Exception ex) {
            audit(tenant, jwt, "resources/read", "unknown", "REJECTED");
            return success(request.id(), toolResult(Map.of("error", concise(ex)), true), sessionId);
        }
    }

    private List<Map<String,Object>> tools() {
        return List.of(
            tool("search_library", "Search published GRACE guides and library resources", objectSchema(Map.of(
                "query", stringSchema("Search terms", 500), "type", stringSchema("Optional content type", 40),
                "subject", stringSchema("Optional subject label", 255), "limit", integerSchema("Maximum results", 1, 20)), List.of("query"))),
            tool("get_published_guide", "Read a published guide by slug", objectSchema(Map.of("slug", stringSchema("Published guide slug", 300)), List.of("slug"))),
            tool("get_library_resource", "Read a published library resource by UUID", objectSchema(Map.of("id", stringSchema("Library resource UUID", 50)), List.of("id"))),
            tool("list_subjects", "List active tenant subjects and their relationships", objectSchema(Map.of(), List.of())),
            tool("get_semantic_graph", "Read the tenant's published JSON-LD knowledge graph", objectSchema(Map.of(), List.of()))
        );
    }

    private List<Map<String,Object>> resourceList(String tenant) {
        return List.of(
            Map.of("uri","grace://"+tenant+"/guides","name","Published guides","description","Published tenant guide collection","mimeType","application/json"),
            Map.of("uri","grace://"+tenant+"/subjects","name","Subject vocabulary","description","Active tenant subject concepts","mimeType","application/json"),
            Map.of("uri","grace://"+tenant+"/semantic/graph","name","Linked-data graph","description","Published JSON-LD knowledge graph","mimeType","application/ld+json")
        );
    }

    private void requireSession(String tenant, String sessionId, String protocolHeader, Jwt jwt) {
        requireAudience(jwt);
        if (!PROTOCOL_VERSION.equals(protocolHeader)) throw new McpSessionException("MCP-Protocol-Version must be " + PROTOCOL_VERSION);
        Session session = sessionId == null ? null : sessions.get(sessionId);
        if (session == null || session.startedAt().plus(SESSION_TTL).isBefore(Instant.now())) throw new McpSessionException("Invalid or expired MCP session");
        if (!session.tenant().equals(tenant) || !session.subject().equals(jwt.getSubject())) throw new McpSessionException("MCP session does not belong to this tenant and subject");
    }

    private void requireAudience(Jwt jwt) {
        if (!jwt.getAudience().contains(audience)) throw new McpSessionException("Access token is not intended for the GRACE MCP resource");
    }

    private void audit(String tenant, Jwt jwt, String operation, String target, String status) {
        jdbc.sql("INSERT INTO mcp_access_events(tenant_slug,actor_id,operation,target_type,status) VALUES(:tenant,:actor,:operation,:target,:status)")
            .param("tenant",tenant).param("actor",UUID.fromString(jwt.getSubject())).param("operation",operation)
            .param("target",target==null?"unknown":target.substring(0,Math.min(target.length(),80))).param("status",status).update();
    }

    private Map<String,Object> toolResult(Object value, boolean error) {
        try {
            String text=value instanceof String string?string:mapper.writeValueAsString(value);
            Map<String,Object> result=new LinkedHashMap<>(); result.put("content",List.of(Map.of("type","text","text",text)));
            if(!(value instanceof String)) result.put("structuredContent",value);
            if(error) result.put("isError",true);
            return result;
        } catch(Exception ex){throw new IllegalStateException(ex);}
    }

    private String jsonLd(Model model) { ByteArrayOutputStream out=new ByteArrayOutputStream(); RDFDataMgr.write(out,model,RDFFormat.JSONLD11_PRETTY); return out.toString(StandardCharsets.UTF_8); }
    private static Map<String,Object> tool(String name,String description,Map<String,Object> schema){return Map.of("name",name,"title",name.replace('_',' '),"description",description,"inputSchema",schema,"annotations",Map.of("readOnlyHint",true,"destructiveHint",false,"idempotentHint",true,"openWorldHint",false));}
    private static Map<String,Object> objectSchema(Map<String,Object> properties,List<String> required){Map<String,Object> schema=new LinkedHashMap<>();schema.put("$schema",JSON_SCHEMA);schema.put("type","object");schema.put("properties",properties);schema.put("required",required);schema.put("additionalProperties",false);return schema;}
    private static Map<String,Object> stringSchema(String description,int max){return Map.of("type","string","description",description,"maxLength",max);}
    private static Map<String,Object> integerSchema(String description,int min,int max){return Map.of("type","integer","description",description,"minimum",min,"maximum",max,"default",10);}
    private static String text(JsonNode parent,String field){return parent==null?"":parent.path(field).asText("");}
    private static String required(JsonNode parent,String field,int max){String value=text(parent,field).trim();if(value.isBlank()||value.length()>max)throw new ToolException(field+" is required and must be at most "+max+" characters");return value;}
    private static String optional(JsonNode parent,String field){String value=text(parent,field).trim();return value.isBlank()?null:value;}
    private static int integer(JsonNode parent,String field,int fallback,int min,int max){int value=parent==null?fallback:parent.path(field).asInt(fallback);if(value<min||value>max)throw new ToolException(field+" must be between "+min+" and "+max);return value;}
    private static String concise(Exception ex){String value=ex.getMessage()==null?ex.getClass().getSimpleName():ex.getMessage();return value.substring(0,Math.min(value.length(),500));}
    private static McpExchange success(Object id,Object result,String sessionId){Map<String,Object> body=new LinkedHashMap<>();body.put("jsonrpc","2.0");body.put("id",id);body.put("result",result);return new McpExchange(200,body,sessionId);}
    private static McpExchange protocolError(Object id,int code,String message){return protocolError(id,code,message,null);}
    private static McpExchange protocolError(Object id,int code,String message,String sessionId){Map<String,Object> body=new LinkedHashMap<>();body.put("jsonrpc","2.0");body.put("id",id);body.put("error",Map.of("code",code,"message",message));return new McpExchange(200,body,sessionId);}
    private record Session(String tenant,String subject,Instant startedAt){}
    private static class ToolException extends RuntimeException{ToolException(String message){super(message);}}
    static class McpSessionException extends RuntimeException{McpSessionException(String message){super(message);}}
}
