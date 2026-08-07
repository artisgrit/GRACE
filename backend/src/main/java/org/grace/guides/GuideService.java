package org.grace.guides;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Array;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.grace.configuration.AuditService;
import org.grace.configuration.JdbcJson;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GuideService {
    private final JdbcClient jdbcClient;
    private final ObjectMapper objectMapper;
    private final AuditService auditService;

    public GuideService(JdbcClient jdbcClient, ObjectMapper objectMapper, AuditService auditService) {
        this.jdbcClient = jdbcClient;
        this.objectMapper = objectMapper;
        this.auditService = auditService;
    }

    public List<GuideType> listTypes(String tenantSlug) {
        return jdbcClient.sql("""
            SELECT gt.id, gt.type_key, gt.display_name, gt.description, gt.system_type
            FROM guide_types gt
            LEFT JOIN tenants t ON t.id = gt.tenant_id
            WHERE gt.enabled = true AND (gt.tenant_id IS NULL OR t.slug = :tenantSlug)
            ORDER BY gt.display_name
            """).param("tenantSlug", tenantSlug).query((rs, rowNum) -> new GuideType(
                rs.getObject("id", UUID.class),
                rs.getString("type_key"),
                rs.getString("display_name"),
                rs.getString("description"),
                rs.getBoolean("system_type")
            )).list();
    }

    public List<GuideTemplate> listTemplates(String tenantSlug) {
        return jdbcClient.sql("""
            SELECT template.id, template.template_key, template.name, template.description,
                   gt.type_key, template.definition
            FROM guide_templates template
            JOIN tenants t ON t.id = template.tenant_id
            JOIN guide_types gt ON gt.id = template.guide_type_id
            WHERE t.slug = :tenantSlug AND template.enabled = true
            ORDER BY template.name
            """).param("tenantSlug", tenantSlug).query((rs, rowNum) -> new GuideTemplate(
                rs.getObject("id", UUID.class),
                rs.getString("template_key"),
                rs.getString("name"),
                rs.getString("description"),
                rs.getString("type_key"),
                JdbcJson.read(rs, "definition", objectMapper)
            )).list();
    }

    public List<GuideSummary> list(String tenantSlug, UUID userId, boolean manageAll) {
        String ownership = manageAll ? "" : "AND (g.owner_id = :userId OR EXISTS (SELECT 1 FROM guide_editors ge WHERE ge.guide_id = g.id AND ge.user_id = :userId))";
        JdbcClient.StatementSpec statement = jdbcClient.sql("""
            SELECT g.id, g.title, g.slug, g.description, gt.type_key, gt.display_name AS type_name,
                   g.status, u.display_name AS owner_name, g.tags, g.revision_number, g.version, g.updated_at
            FROM guides g
            JOIN tenants t ON t.id = g.tenant_id
            JOIN guide_types gt ON gt.id = g.guide_type_id
            JOIN users u ON u.id = g.owner_id
            WHERE t.slug = :tenantSlug AND g.deleted_at IS NULL
            """ + ownership + " ORDER BY g.updated_at DESC")
            .param("tenantSlug", tenantSlug);
        if (!manageAll) {
            statement = statement.param("userId", userId);
        }
        return statement.query((rs, rowNum) -> new GuideSummary(
            rs.getObject("id", UUID.class),
            rs.getString("title"),
            rs.getString("slug"),
            rs.getString("description"),
            rs.getString("type_key"),
            rs.getString("type_name"),
            rs.getString("status"),
            rs.getString("owner_name"),
            readTags(rs.getArray("tags")),
            rs.getInt("revision_number"),
            rs.getLong("version"),
            rs.getObject("updated_at", Timestamp.class).toInstant()
        )).list();
    }

    @Transactional
    public GuideDetail create(String tenantSlug, UUID ownerId, CreateGuideRequest request) {
        UUID tenantId = tenantId(tenantSlug);
        UUID guideTypeId = guideTypeId(tenantId, request.guideTypeKey());
        UUID guideId = UUID.randomUUID();
        jdbcClient.sql("""
            INSERT INTO guides
                (id, tenant_id, guide_type_id, owner_id, title, slug, description, tags, metadata)
            VALUES (:id, :tenantId, :guideTypeId, :ownerId, :title, :slug, :description, :tags, '{}'::jsonb)
            """)
            .param("id", guideId)
            .param("tenantId", tenantId)
            .param("guideTypeId", guideTypeId)
            .param("ownerId", ownerId)
            .param("title", request.title().trim())
            .param("slug", request.slug())
            .param("description", request.description() == null ? "" : request.description().trim())
            .param("tags", tagsArray(request.tags()))
            .update();

        List<GuidePageInput> pages = templatePages(tenantId, request.templateKey());
        if (pages.isEmpty()) {
            pages = List.of(new GuidePageInput(
                UUID.randomUUID(), "Overview", "overview", Map.of("columns", 1), List.of()
            ));
        }
        replacePages(guideId, pages);
        GuideDetail created = get(tenantSlug, guideId);
        recordRevision(created, ownerId, "Guide created");
        auditService.record(tenantSlug, "guides", "created", "Guide created: " + created.title(), Map.of("guideId", guideId));
        return created;
    }

    @Transactional
    public GuideDetail update(String tenantSlug, UUID guideId, UUID authorId, UpdateGuideRequest request) {
        UUID tenantId = tenantId(tenantSlug);
        UUID guideTypeId = guideTypeId(tenantId, request.guideTypeKey());
        int changed = jdbcClient.sql("""
            UPDATE guides SET
                guide_type_id = :guideTypeId, title = :title, slug = :slug,
                description = :description, tags = :tags, metadata = CAST(:metadata AS jsonb),
                featured_image_url = :featuredImageUrl, seo_title = :seoTitle,
                seo_description = :seoDescription, revision_number = revision_number + 1,
                version = version + 1, updated_at = CURRENT_TIMESTAMP
            WHERE id = :guideId AND tenant_id = :tenantId AND version = :version AND deleted_at IS NULL
            """)
            .param("guideTypeId", guideTypeId)
            .param("title", request.title().trim())
            .param("slug", request.slug())
            .param("description", request.description() == null ? "" : request.description().trim())
            .param("tags", tagsArray(request.tags()))
            .param("metadata", JdbcJson.write(request.metadata(), objectMapper))
            .param("featuredImageUrl", request.featuredImageUrl())
            .param("seoTitle", request.seoTitle())
            .param("seoDescription", request.seoDescription())
            .param("guideId", guideId)
            .param("tenantId", tenantId)
            .param("version", request.version())
            .update();
        if (changed != 1) {
            throw new OptimisticLockingFailureException("The guide was changed by another editor; reload before saving");
        }
        replacePages(guideId, request.pages());
        GuideDetail updated = get(tenantSlug, guideId);
        recordRevision(updated, authorId, request.changeSummary());
        auditService.record(tenantSlug, "guides", "updated", request.changeSummary(), Map.of("guideId", guideId, "revision", updated.revisionNumber()));
        return updated;
    }

    public GuideDetail get(String tenantSlug, UUID guideId) {
        return getByWhere("t.slug = :tenantSlug AND g.id = :guideId AND g.deleted_at IS NULL", Map.of(
            "tenantSlug", tenantSlug, "guideId", guideId
        ));
    }

    public GuideDetail getPublished(String tenantSlug, String slug) {
        return getByWhere("t.slug = :tenantSlug AND g.slug = :slug AND g.status = 'PUBLISHED' AND g.deleted_at IS NULL", Map.of(
            "tenantSlug", tenantSlug, "slug", slug
        ));
    }

    public List<GuideSummary> listPublished(String tenantSlug) {
        return jdbcClient.sql("""
            SELECT g.id, g.title, g.slug, g.description, gt.type_key, gt.display_name AS type_name,
                   g.status, u.display_name AS owner_name, g.tags, g.revision_number, g.version, g.updated_at
            FROM guides g JOIN tenants t ON t.id = g.tenant_id
            JOIN guide_types gt ON gt.id = g.guide_type_id JOIN users u ON u.id = g.owner_id
            WHERE t.slug = :tenantSlug AND g.status = 'PUBLISHED' AND g.deleted_at IS NULL
            ORDER BY g.title
            """).param("tenantSlug", tenantSlug).query((rs, rowNum) -> new GuideSummary(
                rs.getObject("id", UUID.class), rs.getString("title"), rs.getString("slug"),
                rs.getString("description"), rs.getString("type_key"), rs.getString("type_name"),
                rs.getString("status"), rs.getString("owner_name"), readTags(rs.getArray("tags")),
                rs.getInt("revision_number"), rs.getLong("version"),
                rs.getObject("updated_at", Timestamp.class).toInstant()
            )).list();
    }

    public boolean canEdit(String tenantSlug, UUID guideId, UUID userId, boolean manageAll) {
        if (manageAll) return true;
        Integer count = jdbcClient.sql("""
            SELECT COUNT(*) FROM guides g JOIN tenants t ON t.id = g.tenant_id
            WHERE t.slug = :tenantSlug AND g.id = :guideId AND g.deleted_at IS NULL
              AND (g.owner_id = :userId OR EXISTS (
                  SELECT 1 FROM guide_editors ge WHERE ge.guide_id = g.id AND ge.user_id = :userId
              ))
            """).param("tenantSlug", tenantSlug).param("guideId", guideId).param("userId", userId)
            .query(Integer.class).single();
        return count == 1;
    }

    public List<GuideRevision> revisions(String tenantSlug, UUID guideId) {
        get(tenantSlug, guideId);
        return jdbcClient.sql("""
            SELECT gr.id, gr.revision_number, u.display_name AS author_name,
                   gr.change_summary, gr.created_at
            FROM guide_revisions gr JOIN users u ON u.id = gr.author_id
            WHERE gr.guide_id = :guideId ORDER BY gr.revision_number DESC
            """).param("guideId", guideId).query((rs, rowNum) -> new GuideRevision(
                rs.getObject("id", UUID.class), rs.getInt("revision_number"),
                rs.getString("author_name"), rs.getString("change_summary"),
                rs.getObject("created_at", Timestamp.class).toInstant()
            )).list();
    }

    @Transactional
    public GuideDetail restore(String tenantSlug, UUID guideId, int revisionNumber, UUID authorId) {
        Object snapshot = jdbcClient.sql("""
            SELECT gr.snapshot FROM guide_revisions gr JOIN guides g ON g.id = gr.guide_id
            JOIN tenants t ON t.id = g.tenant_id
            WHERE t.slug = :tenantSlug AND g.id = :guideId AND gr.revision_number = :revisionNumber
            """).param("tenantSlug", tenantSlug).param("guideId", guideId).param("revisionNumber", revisionNumber)
            .query((rs, rowNum) -> JdbcJson.read(rs, "snapshot", objectMapper)).optional()
            .orElseThrow(() -> new IllegalArgumentException("Unknown guide revision"));
        GuideDetail historical = objectMapper.convertValue(snapshot, GuideDetail.class);
        GuideDetail current = get(tenantSlug, guideId);
        List<GuidePageInput> pages = historical.pages().stream().map(page -> new GuidePageInput(
            page.id(), page.title(), page.slug(), page.layout(), page.blocks().stream().map(block ->
                new GuideBlockInput(block.id(), block.blockType(), block.content(), block.settings())
            ).toList()
        )).toList();
        return update(tenantSlug, guideId, authorId, new UpdateGuideRequest(
            historical.title(), historical.slug(), historical.description(), historical.guideTypeKey(),
            historical.tags(), historical.metadata(), historical.featuredImageUrl(), historical.seoTitle(),
            historical.seoDescription(), current.version(), "Restored revision " + revisionNumber, pages
        ));
    }

    private GuideDetail getByWhere(String where, Map<String, Object> parameters) {
        JdbcClient.StatementSpec statement = jdbcClient.sql("""
            SELECT g.id, t.slug AS tenant_slug, g.title, g.slug, g.description,
                   gt.type_key, gt.display_name AS type_name, g.status, g.owner_id,
                   u.display_name AS owner_name, g.tags, g.metadata, g.featured_image_url,
                   g.seo_title, g.seo_description, g.revision_number, g.version, g.created_at, g.updated_at
            FROM guides g JOIN tenants t ON t.id = g.tenant_id
            JOIN guide_types gt ON gt.id = g.guide_type_id JOIN users u ON u.id = g.owner_id
            """ + "WHERE " + where);
        for (Map.Entry<String, Object> parameter : parameters.entrySet()) {
            statement = statement.param(parameter.getKey(), parameter.getValue());
        }
        return statement.query((rs, rowNum) -> {
            UUID id = rs.getObject("id", UUID.class);
            return new GuideDetail(
                id, rs.getString("tenant_slug"), rs.getString("title"), rs.getString("slug"),
                rs.getString("description"), rs.getString("type_key"), rs.getString("type_name"),
                rs.getString("status"), rs.getObject("owner_id", UUID.class), rs.getString("owner_name"),
                readTags(rs.getArray("tags")), JdbcJson.read(rs, "metadata", objectMapper),
                rs.getString("featured_image_url"), rs.getString("seo_title"), rs.getString("seo_description"),
                rs.getInt("revision_number"), rs.getLong("version"),
                rs.getObject("created_at", Timestamp.class).toInstant(),
                rs.getObject("updated_at", Timestamp.class).toInstant(), pages(id)
            );
        }).optional().orElseThrow(() -> new IllegalArgumentException("Guide was not found"));
    }

    private List<GuidePage> pages(UUID guideId) {
        return jdbcClient.sql("""
            SELECT id, title, slug, position, layout FROM guide_pages
            WHERE guide_id = :guideId ORDER BY position
            """).param("guideId", guideId).query((rs, rowNum) -> {
                UUID pageId = rs.getObject("id", UUID.class);
                return new GuidePage(pageId, rs.getString("title"), rs.getString("slug"),
                    rs.getInt("position"), JdbcJson.read(rs, "layout", objectMapper), blocks(pageId));
            }).list();
    }

    private List<GuideBlock> blocks(UUID pageId) {
        return jdbcClient.sql("""
            SELECT id, block_type, position, content, settings FROM content_blocks
            WHERE page_id = :pageId ORDER BY position
            """).param("pageId", pageId).query((rs, rowNum) -> new GuideBlock(
                rs.getObject("id", UUID.class), rs.getString("block_type"), rs.getInt("position"),
                JdbcJson.read(rs, "content", objectMapper), JdbcJson.read(rs, "settings", objectMapper)
            )).list();
    }

    private void replacePages(UUID guideId, List<GuidePageInput> pages) {
        jdbcClient.sql("DELETE FROM guide_pages WHERE guide_id = :guideId").param("guideId", guideId).update();
        for (int pagePosition = 0; pagePosition < pages.size(); pagePosition++) {
            GuidePageInput page = pages.get(pagePosition);
            UUID pageId = page.id() == null ? UUID.randomUUID() : page.id();
            jdbcClient.sql("""
                INSERT INTO guide_pages (id, guide_id, title, slug, position, layout)
                VALUES (:id, :guideId, :title, :slug, :position, CAST(:layout AS jsonb))
                """).param("id", pageId).param("guideId", guideId).param("title", page.title().trim())
                .param("slug", page.slug()).param("position", pagePosition)
                .param("layout", JdbcJson.write(page.layout(), objectMapper)).update();
            for (int blockPosition = 0; blockPosition < page.blocks().size(); blockPosition++) {
                GuideBlockInput block = page.blocks().get(blockPosition);
                jdbcClient.sql("""
                    INSERT INTO content_blocks (id, page_id, block_type, position, content, settings)
                    VALUES (:id, :pageId, :blockType, :position, CAST(:content AS jsonb), CAST(:settings AS jsonb))
                    """).param("id", block.id() == null ? UUID.randomUUID() : block.id())
                    .param("pageId", pageId).param("blockType", block.blockType())
                    .param("position", blockPosition)
                    .param("content", JdbcJson.write(block.content(), objectMapper))
                    .param("settings", JdbcJson.write(block.settings(), objectMapper)).update();
            }
        }
    }

    private List<GuidePageInput> templatePages(UUID tenantId, String templateKey) {
        if (templateKey == null || templateKey.isBlank()) return List.of();
        Object definition = jdbcClient.sql("""
            SELECT definition FROM guide_templates
            WHERE tenant_id = :tenantId AND template_key = :templateKey AND enabled = true
            """).param("tenantId", tenantId).param("templateKey", templateKey)
            .query((rs, rowNum) -> JdbcJson.read(rs, "definition", objectMapper)).optional()
            .orElseThrow(() -> new IllegalArgumentException("Unknown guide template"));
        TemplateDefinition template = objectMapper.convertValue(definition, TemplateDefinition.class);
        List<GuidePageInput> pages = new ArrayList<>();
        for (TemplatePage page : template.pages()) {
            pages.add(new GuidePageInput(UUID.randomUUID(), page.title(), page.slug(), page.layout(),
                page.blocks().stream().map(block -> new GuideBlockInput(
                    UUID.randomUUID(), block.type(), block.content(), block.settings()
                )).toList()));
        }
        return pages;
    }

    private void recordRevision(GuideDetail guide, UUID authorId, String summary) {
        jdbcClient.sql("""
            INSERT INTO guide_revisions (guide_id, revision_number, author_id, change_summary, snapshot)
            VALUES (:guideId, :revision, :authorId, :summary, CAST(:snapshot AS jsonb))
            """).param("guideId", guide.id()).param("revision", guide.revisionNumber())
            .param("authorId", authorId).param("summary", summary)
            .param("snapshot", JdbcJson.write(guide, objectMapper)).update();
    }

    private UUID tenantId(String tenantSlug) {
        return jdbcClient.sql("SELECT id FROM tenants WHERE slug = :slug AND enabled = true")
            .param("slug", tenantSlug).query(UUID.class).optional()
            .orElseThrow(() -> new IllegalArgumentException("Unknown or disabled tenant"));
    }

    private UUID guideTypeId(UUID tenantId, String typeKey) {
        return jdbcClient.sql("""
            SELECT id FROM guide_types WHERE type_key = :typeKey AND enabled = true
              AND (tenant_id IS NULL OR tenant_id = :tenantId)
            ORDER BY tenant_id NULLS LAST LIMIT 1
            """).param("typeKey", typeKey).param("tenantId", tenantId).query(UUID.class).optional()
            .orElseThrow(() -> new IllegalArgumentException("Unknown guide type"));
    }

    private String[] tagsArray(List<String> tags) {
        if (tags == null) return new String[0];
        return tags.stream().map(String::trim).filter(tag -> !tag.isBlank()).distinct().toArray(String[]::new);
    }

    private List<String> readTags(Array array) {
        try {
            return array == null ? List.of() : Arrays.asList((String[]) array.getArray());
        } catch (java.sql.SQLException ex) {
            throw new IllegalStateException("Could not read guide tags", ex);
        }
    }

    private record TemplateDefinition(List<TemplatePage> pages) {}
    private record TemplatePage(String title, String slug, Object layout, List<TemplateBlock> blocks) {}
    private record TemplateBlock(String type, Object content, Object settings) {}
}
