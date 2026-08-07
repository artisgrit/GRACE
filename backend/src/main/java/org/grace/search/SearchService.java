package org.grace.search;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Array;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class SearchService {
    private static final String INDEX = "grace-content-v1";
    private final JdbcClient jdbc;
    private final ObjectMapper mapper;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
    private final URI endpoint;

    public SearchService(JdbcClient jdbc, ObjectMapper mapper,
                         @Value("${grace.search.url:http://localhost:9200}") String endpoint) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.endpoint = URI.create(endpoint.replaceAll("/+$", ""));
    }

    public SearchResponse search(String tenant, String query, String type, String subject, int page, int size) {
        String q = query == null ? "" : query.trim();
        int safePage = Math.max(0, page);
        int safeSize = Math.max(1, Math.min(size, 50));
        try {
            ensureIndex();
            Map<String, Object> bool = new LinkedHashMap<>();
            bool.put("must", q.isBlank()
                ? List.of(Map.of("match_all", Map.of()))
                : List.of(Map.of("multi_match", Map.of("query", q, "fields", List.of("title^4", "description^2", "content", "tags", "subjects"), "fuzziness", "AUTO"))));
            List<Object> filters = new ArrayList<>();
            filters.add(Map.of("term", Map.of("tenant", tenant)));
            if (type != null && !type.isBlank()) filters.add(Map.of("term", Map.of("type", type.toUpperCase())));
            if (subject != null && !subject.isBlank()) filters.add(Map.of("term", Map.of("subjects", subject)));
            bool.put("filter", filters);
            Map<String, Object> body = Map.of(
                "from", safePage * safeSize, "size", safeSize,
                "query", Map.of("bool", bool),
                "aggs", Map.of(
                    "types", Map.of("terms", Map.of("field", "type", "size", 20)),
                    "subjects", Map.of("terms", Map.of("field", "subjects", "size", 50)))
            );
            JsonNode root = send("POST", "/" + INDEX + "/_search", mapper.writeValueAsString(body));
            List<SearchResponse.SearchItem> items = new ArrayList<>();
            root.path("hits").path("hits").forEach(hit -> {
                JsonNode source = hit.path("_source");
                items.add(new SearchResponse.SearchItem(source.path("id").asText(), source.path("type").asText(),
                    source.path("title").asText(), source.path("description").asText(), source.path("url").asText(),
                    strings(source.path("subjects")), strings(source.path("tags")), hit.path("_score").asDouble()));
            });
            Map<String, Map<String, Long>> facets = Map.of(
                "types", buckets(root.path("aggregations").path("types").path("buckets")),
                "subjects", buckets(root.path("aggregations").path("subjects").path("buckets")));
            return new SearchResponse(q, root.path("hits").path("total").path("value").asLong(), items, facets, false, "opensearch");
        } catch (Exception unavailable) {
            return fallback(tenant, q, type, subject, safePage, safeSize);
        }
    }

    public List<String> autocomplete(String tenant, String query) {
        if (query == null || query.trim().length() < 2) return List.of();
        String prefix = query.trim();
        try {
            ensureIndex();
            Map<String,Object> body = Map.of("size", 8, "_source", List.of("title"), "query", Map.of("bool", Map.of(
                "must", List.of(Map.of("match_phrase_prefix", Map.of("title", Map.of("query", prefix, "max_expansions", 25)))),
                "filter", List.of(Map.of("term", Map.of("tenant", tenant))))));
            JsonNode root = send("POST", "/" + INDEX + "/_search", mapper.writeValueAsString(body));
            List<String> titles = new ArrayList<>();
            root.path("hits").path("hits").forEach(hit -> titles.add(hit.path("_source").path("title").asText()));
            return titles.stream().distinct().toList();
        } catch (Exception unavailable) {
            String needle = prefix.toLowerCase();
            return documents(tenant).stream().map(document -> document.get("title").toString())
                .filter(title -> title.toLowerCase().contains(needle)).distinct().limit(8).toList();
        }
    }

    public Map<String, Object> reindex(String tenant) {
        UUID run = UUID.randomUUID();
        jdbc.sql("INSERT INTO search_index_runs (id, tenant_slug, status) VALUES (:id, :tenant, 'RUNNING')")
            .param("id", run).param("tenant", tenant).update();
        try {
            ensureIndex();
            send("POST", "/" + INDEX + "/_delete_by_query?refresh=true",
                mapper.writeValueAsString(Map.of("query", Map.of("term", Map.of("tenant", tenant)))));
            List<Map<String, Object>> documents = documents(tenant);
            if (!documents.isEmpty()) {
                StringBuilder bulk = new StringBuilder();
                for (Map<String, Object> document : documents) {
                    bulk.append(mapper.writeValueAsString(Map.of("index", Map.of("_index", INDEX, "_id", tenant + ":" + document.get("type") + ":" + document.get("id"))))).append('\n');
                    bulk.append(mapper.writeValueAsString(document)).append('\n');
                }
                sendRaw("POST", "/_bulk?refresh=true", bulk.toString(), "application/x-ndjson");
            }
            jdbc.sql("UPDATE search_index_runs SET status='SUCCEEDED', documents_indexed=:count, completed_at=CURRENT_TIMESTAMP WHERE id=:id")
                .param("count", documents.size()).param("id", run).update();
            return Map.of("runId", run, "status", "SUCCEEDED", "documentsIndexed", documents.size());
        } catch (Exception ex) {
            jdbc.sql("UPDATE search_index_runs SET status='FAILED', error_message=:error, completed_at=CURRENT_TIMESTAMP WHERE id=:id")
                .param("error", concise(ex)).param("id", run).update();
            return Map.of("runId", run, "status", "FAILED", "documentsIndexed", 0, "error", concise(ex));
        }
    }

    public Map<String, Object> status(String tenant) {
        Map<String, Object> latest = jdbc.sql("""
            SELECT id, status, documents_indexed, started_at, completed_at, error_message
            FROM search_index_runs WHERE tenant_slug=:tenant ORDER BY started_at DESC LIMIT 1
            """).param("tenant", tenant).query((rs, n) -> {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("runId", rs.getObject("id")); row.put("status", rs.getString("status"));
                row.put("documentsIndexed", rs.getInt("documents_indexed")); row.put("startedAt", instant(rs.getTimestamp("started_at")));
                row.put("completedAt", instant(rs.getTimestamp("completed_at"))); row.put("error", rs.getString("error_message"));
                return row;
            }).optional().orElseGet(() -> Map.of("status", "NOT_INDEXED"));
        boolean available;
        try { send("GET", "/_cluster/health", null); available = true; } catch (Exception ex) { available = false; }
        Map<String, Object> result = new LinkedHashMap<>(latest); result.put("available", available); result.put("index", INDEX);
        return result;
    }

    @Scheduled(fixedDelayString = "${grace.search.sync-interval-ms:60000}", initialDelayString = "${grace.search.initial-delay-ms:15000}")
    public void synchronizePublishedContent() {
        jdbc.sql("SELECT slug FROM tenants WHERE enabled=true").query(String.class).list().forEach(this::reindex);
    }

    private List<Map<String, Object>> documents(String tenant) {
        return jdbc.sql("""
            SELECT g.id::text id, 'GUIDE' type, g.title, g.description, '/guides/' || g.slug url,
                   g.tags, COALESCE(array_agg(DISTINCT s.pref_label) FILTER (WHERE s.id IS NOT NULL), '{}') subjects,
                   COALESCE(string_agg(DISTINCT cb.content::text, ' '), '') content
            FROM guides g JOIN tenants t ON t.id=g.tenant_id LEFT JOIN guide_subjects gs ON gs.guide_id=g.id
            LEFT JOIN subjects s ON s.id=gs.subject_id LEFT JOIN guide_pages gp ON gp.guide_id=g.id
            LEFT JOIN content_blocks cb ON cb.page_id=gp.id
            WHERE t.slug=:tenant AND g.status='PUBLISHED' AND g.deleted_at IS NULL
            GROUP BY g.id
            UNION ALL
            SELECT r.id::text, r.resource_type, r.title, r.description,
                   COALESCE(r.canonical_url, '/resources?type=' || lower(r.resource_type)), r.authors,
                   COALESCE(array_agg(DISTINCT s.pref_label) FILTER (WHERE s.id IS NOT NULL), '{}'),
                   concat_ws(' ', r.alternative_title, r.provider, r.publisher, r.faq_answer)
            FROM library_resources r JOIN tenants t ON t.id=r.tenant_id LEFT JOIN resource_subjects rs ON rs.resource_id=r.id
            LEFT JOIN subjects s ON s.id=rs.subject_id
            WHERE t.slug=:tenant AND r.status='PUBLISHED' AND r.deleted_at IS NULL
            GROUP BY r.id
            """).param("tenant", tenant).query((rs, n) -> {
                Map<String, Object> doc = new LinkedHashMap<>();
                doc.put("id", rs.getString("id")); doc.put("tenant", tenant); doc.put("type", rs.getString("type"));
                doc.put("title", rs.getString("title")); doc.put("description", rs.getString("description"));
                doc.put("url", rs.getString("url")); doc.put("tags", array(rs.getArray("tags")));
                doc.put("subjects", array(rs.getArray("subjects"))); doc.put("content", rs.getString("content"));
                return doc;
            }).list();
    }

    private SearchResponse fallback(String tenant, String q, String type, String subject, int page, int size) {
        List<Map<String, Object>> all = documents(tenant).stream().filter(d -> {
            String haystack = (d.get("title") + " " + d.get("description") + " " + d.get("content")).toLowerCase();
            boolean queryMatch = q.isBlank() || haystack.contains(q.toLowerCase());
            boolean typeMatch = type == null || type.isBlank() || d.get("type").toString().equalsIgnoreCase(type);
            boolean subjectMatch = subject == null || subject.isBlank() || ((List<?>) d.get("subjects")).contains(subject);
            return queryMatch && typeMatch && subjectMatch;
        }).toList();
        List<SearchResponse.SearchItem> items = all.stream().skip((long) page * size).limit(size).map(d ->
            new SearchResponse.SearchItem(d.get("id").toString(), d.get("type").toString(), d.get("title").toString(),
                d.get("description").toString(), d.get("url").toString(), cast(d.get("subjects")), cast(d.get("tags")), 0)).toList();
        Map<String, Long> types = new LinkedHashMap<>(); Map<String, Long> subjects = new LinkedHashMap<>();
        all.forEach(d -> { types.merge(d.get("type").toString(), 1L, Long::sum); cast(d.get("subjects")).forEach(s -> subjects.merge(s, 1L, Long::sum)); });
        return new SearchResponse(q, all.size(), items, Map.of("types", types, "subjects", subjects), true, "postgresql");
    }

    private void ensureIndex() throws Exception {
        HttpRequest head = HttpRequest.newBuilder(endpoint.resolve("/" + INDEX)).timeout(Duration.ofSeconds(3)).method("HEAD", HttpRequest.BodyPublishers.noBody()).build();
        int status = http.send(head, HttpResponse.BodyHandlers.discarding()).statusCode();
        if (status == 404) send("PUT", "/" + INDEX, mapper.writeValueAsString(Map.of("mappings", Map.of("properties", Map.of(
            "tenant", Map.of("type", "keyword"), "type", Map.of("type", "keyword"), "title", Map.of("type", "text", "fields", Map.of("keyword", Map.of("type", "keyword"))),
            "description", Map.of("type", "text"), "content", Map.of("type", "text"), "subjects", Map.of("type", "keyword"), "tags", Map.of("type", "keyword"))))));
        else if (status >= 400) throw new IllegalStateException("OpenSearch returned " + status);
    }

    private JsonNode send(String method, String path, String body) throws Exception { return mapper.readTree(sendRaw(method, path, body, "application/json")); }
    private String sendRaw(String method, String path, String body, String contentType) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(endpoint.resolve(path)).timeout(Duration.ofSeconds(10)).header("Content-Type", contentType);
        HttpRequest request = builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body)).build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 400) throw new IllegalStateException("OpenSearch returned " + response.statusCode());
        return response.body().isBlank() ? "{}" : response.body();
    }
    private static Map<String, Long> buckets(JsonNode buckets) { Map<String, Long> result = new LinkedHashMap<>(); buckets.forEach(b -> result.put(b.path("key").asText(), b.path("doc_count").asLong())); return result; }
    private static List<String> strings(JsonNode node) { List<String> result = new ArrayList<>(); node.forEach(n -> result.add(n.asText())); return result; }
    private static List<String> array(Array value) throws java.sql.SQLException { return value == null ? List.of() : List.of((String[]) value.getArray()); }
    @SuppressWarnings("unchecked") private static List<String> cast(Object value) { return (List<String>) value; }
    private static Instant instant(Timestamp value) { return value == null ? null : value.toInstant(); }
    private static String concise(Exception ex) { String message = ex.getMessage(); return (message == null ? ex.getClass().getSimpleName() : message).substring(0, Math.min(message == null ? ex.getClass().getSimpleName().length() : message.length(), 1000)); }
}
