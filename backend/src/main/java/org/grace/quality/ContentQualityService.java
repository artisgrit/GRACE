package org.grace.quality;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.grace.configuration.AuditService;
import org.grace.configuration.JdbcJson;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class ContentQualityService {
    private final JdbcClient jdbc;
    private final ObjectMapper mapper;
    private final AuditService audit;
    private final HttpClient http = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(3))
        .followRedirects(HttpClient.Redirect.NEVER)
        .build();

    public ContentQualityService(JdbcClient jdbc, ObjectMapper mapper, AuditService audit) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.audit = audit;
    }

    public Map<String, Object> run(String tenant) {
        int checked = checkLinks(tenant);
        int assessed = assess(tenant);
        int reminders = remind(tenant);
        jdbc.sql("DELETE FROM link_check_results WHERE checked_at < CURRENT_TIMESTAMP - interval '90 days'").update();
        Map<String, Object> result = Map.of(
            "linksChecked", checked,
            "guidesAssessed", assessed,
            "remindersCreated", reminders
        );
        audit.record(tenant, "quality", "assessment.completed", "Content quality assessment completed", result);
        return result;
    }

    public List<Map<String, Object>> assessments(String tenant) {
        return jdbc.sql("""
            SELECT q.guide_id, g.title, g.slug, q.health_score, q.accessibility_issues,
                   q.broken_links, q.review_overdue, q.issues, q.assessed_at
            FROM content_quality_assessments q
            JOIN guides g ON g.id = q.guide_id
            WHERE q.tenant_slug = :tenant
            ORDER BY q.health_score, g.title
            """).param("tenant", tenant).query((rs, row) -> {
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("guideId", rs.getObject("guide_id"));
                result.put("title", rs.getString("title"));
                result.put("slug", rs.getString("slug"));
                result.put("healthScore", rs.getInt("health_score"));
                result.put("accessibilityIssues", rs.getInt("accessibility_issues"));
                result.put("brokenLinks", rs.getInt("broken_links"));
                result.put("reviewOverdue", rs.getBoolean("review_overdue"));
                result.put("issues", JdbcJson.read(rs, "issues", mapper));
                result.put("assessedAt", rs.getTimestamp("assessed_at").toInstant());
                return result;
            }).list();
    }

    public List<Map<String, Object>> notifications(String tenant, UUID userId) {
        return jdbc.sql("""
            SELECT id, notification_type, title, body, entity_type, entity_id, read_at, created_at
            FROM user_notifications
            WHERE tenant_slug = :tenant AND recipient_id = :user
            ORDER BY created_at DESC LIMIT 50
            """).param("tenant", tenant).param("user", userId).query((rs, row) -> {
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("id", rs.getObject("id"));
                result.put("type", rs.getString("notification_type"));
                result.put("title", rs.getString("title"));
                result.put("body", rs.getString("body"));
                result.put("entityType", rs.getString("entity_type"));
                result.put("entityId", rs.getObject("entity_id"));
                result.put("read", rs.getTimestamp("read_at") != null);
                result.put("createdAt", rs.getTimestamp("created_at").toInstant());
                return result;
            }).list();
    }

    @Scheduled(
        fixedDelayString = "${grace.quality.interval-ms:21600000}",
        initialDelayString = "${grace.quality.initial-delay-ms:30000}"
    )
    public void scheduled() {
        jdbc.sql("SELECT slug FROM tenants WHERE enabled = true").query(String.class).list().forEach(tenant -> {
            try {
                run(tenant);
            } catch (RuntimeException ignored) {
                // A tenant assessment must not stop later scheduled tenant work.
            }
        });
    }

    private int checkLinks(String tenant) {
        List<Link> links = new ArrayList<>();
        links.addAll(jdbc.sql("""
            SELECT r.id, r.canonical_url
            FROM library_resources r JOIN tenants t ON t.id = r.tenant_id
            WHERE t.slug = :tenant AND r.status = 'PUBLISHED' AND r.deleted_at IS NULL
              AND r.canonical_url IS NOT NULL
            """).param("tenant", tenant).query((rs, row) ->
                new Link("RESOURCE", rs.getObject("id", UUID.class), rs.getString("canonical_url"))).list());

        jdbc.sql("""
            SELECT g.id, cb.content
            FROM guides g JOIN tenants t ON t.id = g.tenant_id
            JOIN guide_pages p ON p.guide_id = g.id JOIN content_blocks cb ON cb.page_id = p.id
            WHERE t.slug = :tenant AND g.status = 'PUBLISHED' AND g.deleted_at IS NULL
            """).param("tenant", tenant).query((rs, row) ->
                Map.entry(rs.getObject("id", UUID.class), JdbcJson.read(rs, "content", mapper))).list()
            .forEach(entry -> {
                Set<String> urls = new LinkedHashSet<>();
                collectUrls(mapper.valueToTree(entry.getValue()), urls);
                urls.forEach(url -> links.add(new Link("GUIDE", entry.getKey(), url)));
            });

        List<Link> distinct = links.stream().distinct().toList();
        distinct.forEach(link -> storeCheck(tenant, link));
        return distinct.size();
    }

    private void storeCheck(String tenant, Link link) {
        String status = "HEALTHY";
        String error = null;
        Integer code = null;
        try {
            URI uri = validate(link.url());
            HttpResponse<Void> response = http.send(HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(8))
                .method("HEAD", HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.discarding());
            code = response.statusCode();
            if (code == 405) {
                response = http.send(HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(8)).GET().build(),
                    HttpResponse.BodyHandlers.discarding());
                code = response.statusCode();
            }
            if (code < 200 || code >= 400) {
                status = "BROKEN";
                error = "HTTP " + code;
            }
        } catch (IllegalArgumentException ex) {
            status = "BLOCKED";
            error = concise(ex);
        } catch (Exception ex) {
            status = "BROKEN";
            error = concise(ex);
        }
        jdbc.sql("""
            INSERT INTO link_check_results
                (tenant_slug, entity_type, entity_id, url, status, http_status, error_message)
            VALUES (:tenant, :type, :id, :url, :status, :code, :error)
            """).param("tenant", tenant).param("type", link.type()).param("id", link.id())
            .param("url", link.url()).param("status", status).param("code", code).param("error", error).update();
    }

    private int assess(String tenant) {
        List<GuideQuality> guides = jdbc.sql("""
            SELECT g.id, g.description, g.next_review_at,
                   COALESCE(jsonb_agg(jsonb_build_object('type', cb.block_type, 'content', cb.content))
                       FILTER (WHERE cb.id IS NOT NULL), '[]') content
            FROM guides g JOIN tenants t ON t.id = g.tenant_id
            LEFT JOIN guide_pages p ON p.guide_id = g.id LEFT JOIN content_blocks cb ON cb.page_id = p.id
            WHERE t.slug = :tenant AND g.status = 'PUBLISHED' AND g.deleted_at IS NULL
            GROUP BY g.id
            """).param("tenant", tenant).query((rs, row) -> new GuideQuality(
                rs.getObject("id", UUID.class), rs.getString("description"),
                rs.getTimestamp("next_review_at") == null ? null : rs.getTimestamp("next_review_at").toInstant(),
                JdbcJson.read(rs, "content", mapper)
            )).list();
        guides.forEach(guide -> assessGuide(tenant, guide));
        return guides.size();
    }

    private void assessGuide(String tenant, GuideQuality guide) {
        List<Map<String, Object>> issues = new ArrayList<>();
        int accessibility = imageAltIssues(guide.content(), issues);
        int broken = latestBrokenLinks(tenant, guide.id());
        boolean overdue = guide.nextReview() != null && guide.nextReview().isBefore(Instant.now());
        boolean missingDescription = guide.description() == null || guide.description().isBlank();
        if (overdue) issues.add(issue("review-overdue", "Guide review date has passed"));
        if (missingDescription) issues.add(issue("missing-description", "Guide needs a public description"));
        if (broken > 0) issues.add(issue("broken-links", broken + " checked link(s) need attention"));
        int score = Math.max(0, 100 - accessibility * 10 - broken * 25 - (overdue ? 20 : 0) - (missingDescription ? 10 : 0));

        jdbc.sql("""
            INSERT INTO content_quality_assessments
                (guide_id, tenant_slug, health_score, accessibility_issues, broken_links, review_overdue, issues)
            VALUES (:id, :tenant, :score, :accessibility, :broken, :overdue, CAST(:issues AS jsonb))
            ON CONFLICT (guide_id) DO UPDATE SET
                health_score = EXCLUDED.health_score, accessibility_issues = EXCLUDED.accessibility_issues,
                broken_links = EXCLUDED.broken_links, review_overdue = EXCLUDED.review_overdue,
                issues = EXCLUDED.issues, assessed_at = CURRENT_TIMESTAMP
            """).param("id", guide.id()).param("tenant", tenant).param("score", score)
            .param("accessibility", accessibility).param("broken", broken).param("overdue", overdue)
            .param("issues", JdbcJson.write(issues, mapper)).update();
    }

    private int imageAltIssues(Object content, List<Map<String, Object>> issues) {
        int count = 0;
        for (JsonNode block : mapper.valueToTree(content)) {
            JsonNode value = block.path("content");
            if (block.path("type").asText().equals("IMAGE") && value.has("url") && !value.path("url").asText().isBlank()
                && (!value.has("alt") || value.path("alt").asText().isBlank())) {
                count++;
                issues.add(issue("image-alt", "Image content requires alternative text"));
            }
        }
        return count;
    }

    private int latestBrokenLinks(String tenant, UUID guideId) {
        return jdbc.sql("""
            SELECT count(*) FROM link_check_results l
            WHERE tenant_slug = :tenant AND entity_type = 'GUIDE' AND entity_id = :id AND status <> 'HEALTHY'
              AND checked_at = (SELECT max(checked_at) FROM link_check_results x
                  WHERE x.tenant_slug = l.tenant_slug AND x.entity_type = l.entity_type
                    AND x.entity_id = l.entity_id AND x.url = l.url)
            """).param("tenant", tenant).param("id", guideId).query(Integer.class).single();
    }

    private int remind(String tenant) {
        return jdbc.sql("""
            INSERT INTO user_notifications
                (tenant_slug, recipient_id, notification_type, title, body, entity_type, entity_id, deduplication_key)
            SELECT :tenant, g.owner_id, 'REVIEW_DUE', 'Guide review due',
                   g.title || ' is due for content review.', 'GUIDE', g.id,
                   'review:' || g.id || ':' || CURRENT_DATE
            FROM guides g JOIN tenants t ON t.id = g.tenant_id
            WHERE t.slug = :tenant AND g.status IN ('PUBLISHED', 'REVIEW_DUE')
              AND g.next_review_at <= CURRENT_TIMESTAMP
            ON CONFLICT (tenant_slug, deduplication_key) DO NOTHING
            """).param("tenant", tenant).update();
    }

    private static URI validate(String value) {
        URI uri = URI.create(value);
        if (uri.getHost() == null || !("http".equals(uri.getScheme()) || "https".equals(uri.getScheme()))) {
            throw new IllegalArgumentException("Only absolute HTTP(S) links are checked");
        }
        try {
            for (InetAddress address : InetAddress.getAllByName(uri.getHost())) {
                if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                    || address.isSiteLocalAddress()) {
                    throw new IllegalArgumentException("Private network link blocked");
                }
            }
        } catch (java.net.UnknownHostException ex) {
            throw new IllegalArgumentException("Host could not be resolved");
        }
        return uri;
    }

    private static void collectUrls(JsonNode node, Set<String> urls) {
        if (node.isObject()) {
            node.properties().forEach(field -> {
                if ((field.getKey().equalsIgnoreCase("url") || field.getKey().equalsIgnoreCase("href"))
                    && field.getValue().isTextual() && field.getValue().asText().startsWith("http")) {
                    urls.add(field.getValue().asText());
                }
                collectUrls(field.getValue(), urls);
            });
        } else if (node.isArray()) {
            node.forEach(child -> collectUrls(child, urls));
        }
    }

    private static Map<String, Object> issue(String code, String message) {
        return Map.of("code", code, "message", message);
    }

    private static String concise(Exception ex) {
        String value = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
        return value.substring(0, Math.min(value.length(), 1000));
    }

    private record Link(String type, UUID id, String url) {}
    private record GuideQuality(UUID id, String description, Instant nextReview, Object content) {}
}
