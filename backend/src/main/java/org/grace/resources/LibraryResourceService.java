package org.grace.resources;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.sql.Array;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.grace.configuration.AuditService;
import org.grace.configuration.JdbcJson;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LibraryResourceService {
    private static final Set<String> TYPES = Set.of("DATABASE", "LIBRARIAN", "FAQ", "BOOK", "READING_LIST");
    private static final Set<String> STATUSES = Set.of("DRAFT", "PUBLISHED", "ARCHIVED");
    private static final Set<String> PROXIES = Set.of("NONE", "EZPROXY", "OPENATHENS", "SHIBBOLETH", "INSTITUTIONAL");
    private final JdbcClient jdbc;
    private final ObjectMapper mapper;
    private final AuditService audit;

    public LibraryResourceService(JdbcClient jdbc, ObjectMapper mapper, AuditService audit) {
        this.jdbc = jdbc; this.mapper = mapper; this.audit = audit;
    }

    public List<LibraryResource> list(String tenantSlug, String type, String query, boolean publicOnly) {
        String normalizedType = blank(type) ? null : normalizeType(type);
        String search = blank(query) ? null : query.trim();
        StringBuilder sql = new StringBuilder("""
            SELECT r.* FROM library_resources r JOIN tenants t ON t.id = r.tenant_id
            WHERE t.slug = :tenantSlug AND r.deleted_at IS NULL
            """);
        if (normalizedType != null) sql.append(" AND r.resource_type = :type");
        if (publicOnly) sql.append(" AND r.status = 'PUBLISHED'");
        if (search != null) sql.append("""
             AND (r.search_vector @@ websearch_to_tsquery('simple', :query)
                  OR r.title ILIKE '%' || :query || '%')
            """);
        sql.append(" ORDER BY r.featured DESC, r.title LIMIT 250");
        JdbcClient.StatementSpec statement = jdbc.sql(sql.toString()).param("tenantSlug", tenantSlug);
        if (normalizedType != null) statement = statement.param("type", normalizedType);
        if (search != null) statement = statement.param("query", search);
        return statement.query((rs, n) -> mapResource(rs)).list();
    }

    public LibraryResource get(String tenantSlug, UUID id, boolean publicOnly) {
        return jdbc.sql("""
            SELECT r.* FROM library_resources r JOIN tenants t ON t.id = r.tenant_id
            WHERE t.slug = :tenantSlug AND r.id = :id AND r.deleted_at IS NULL
              AND (:publicOnly = false OR r.status = 'PUBLISHED')
            """).param("tenantSlug", tenantSlug).param("id", id).param("publicOnly", publicOnly)
            .query((rs, n) -> mapResource(rs)).optional()
            .orElseThrow(() -> new IllegalArgumentException("Library resource not found"));
    }

    @Transactional
    public LibraryResource create(String tenantSlug, UUID actorId, LibraryResourceRequest request) {
        validate(request);
        validateRelations(tenantSlug, request);
        UUID id = UUID.randomUUID();
        JdbcClient.StatementSpec statement = jdbc.sql("""
            INSERT INTO library_resources
                (id, tenant_id, resource_type, title, slug, alternative_title, description, status, provider,
                 publisher, canonical_url, proxy_mode, access_type, isbn, authors, email, phone, orcid,
                 faq_answer, featured, open_access, trial_ends_at, metadata, created_by, updated_by)
            SELECT :id, t.id, :type, :title, :slug, :alternativeTitle, :description, :status, :provider,
                   :publisher, :url, :proxyMode, :accessType, :isbn, :authors, :email, :phone, :orcid,
                   :faqAnswer, :featured, :openAccess, :trialEndsAt, CAST(:metadata AS jsonb), :actorId, :actorId
            FROM tenants t WHERE t.slug = :tenantSlug
            """).param("id", id).param("tenantSlug", tenantSlug).param("actorId", actorId);
        bindWrite(statement, request).update();
        replaceRelations(tenantSlug, id, request);
        audit.record(tenantSlug, "resources", "create", "Created " + request.resourceType() + " " + request.title(), Map.of("id", id));
        return get(tenantSlug, id, false);
    }

    @Transactional
    public LibraryResource update(String tenantSlug, UUID id, UUID actorId, LibraryResourceRequest request) {
        validate(request);
        validateRelations(tenantSlug, request);
        JdbcClient.StatementSpec statement = jdbc.sql("""
            UPDATE library_resources r SET resource_type = :type, title = :title, slug = :slug,
                alternative_title = :alternativeTitle, description = :description, status = :status,
                provider = :provider, publisher = :publisher, canonical_url = :url, proxy_mode = :proxyMode,
                access_type = :accessType, isbn = :isbn, authors = :authors, email = :email, phone = :phone,
                orcid = :orcid, faq_answer = :faqAnswer, featured = :featured, open_access = :openAccess,
                trial_ends_at = :trialEndsAt, metadata = CAST(:metadata AS jsonb), updated_by = :actorId,
                version = r.version + 1, updated_at = CURRENT_TIMESTAMP
            FROM tenants t WHERE r.tenant_id = t.id AND t.slug = :tenantSlug AND r.id = :id
              AND r.version = :version AND r.deleted_at IS NULL
            """).param("id", id).param("tenantSlug", tenantSlug).param("actorId", actorId)
            .param("version", request.version());
        int changed = bindWrite(statement, request).update();
        if (changed == 0) throw new OptimisticLockingFailureException("The resource was changed by another editor");
        replaceRelations(tenantSlug, id, request);
        audit.record(tenantSlug, "resources", "update", "Updated " + request.resourceType() + " " + request.title(), Map.of("id", id));
        return get(tenantSlug, id, false);
    }

    private JdbcClient.StatementSpec bindWrite(JdbcClient.StatementSpec statement, LibraryResourceRequest r) {
        return statement.param("type", normalizeType(r.resourceType())).param("title", r.title().trim())
            .param("slug", r.slug()).param("alternativeTitle", nullable(r.alternativeTitle()))
            .param("description", value(r.description())).param("status", r.status().toUpperCase(Locale.ROOT))
            .param("provider", nullable(r.provider())).param("publisher", nullable(r.publisher()))
            .param("url", nullable(r.canonicalUrl())).param("proxyMode", r.proxyMode().toUpperCase(Locale.ROOT))
            .param("accessType", nullable(r.accessType())).param("isbn", nullable(r.isbn()))
            .param("authors", r.authors().toArray(String[]::new)).param("email", nullable(r.email()))
            .param("phone", nullable(r.phone())).param("orcid", nullable(r.orcid()))
            .param("faqAnswer", nullable(r.faqAnswer())).param("featured", r.featured())
            .param("openAccess", r.openAccess()).param("trialEndsAt", r.trialEndsAt())
            .param("metadata", JdbcJson.write(r.metadata(), mapper));
    }

    @Transactional
    public SubjectRecord createSubject(String tenantSlug, SubjectRequest request) {
        UUID id = UUID.randomUUID();
        validateSubject(tenantSlug, id, request);
        jdbc.sql("""
            INSERT INTO subjects (id, tenant_id, pref_label, slug, alt_labels, broader_term_id, definition, notation, is_active)
            SELECT :id, t.id, :label, :slug, :aliases, :broader, :description, :notation, :active
            FROM tenants t WHERE t.slug = :tenantSlug
            """).param("id", id).param("tenantSlug", tenantSlug).param("label", request.label().trim())
            .param("slug", request.slug()).param("aliases", request.alternativeLabels().toArray(String[]::new))
            .param("broader", request.broaderSubjectId()).param("description", value(request.description()))
            .param("notation", nullable(request.notation())).param("active", request.active()).update();
        replaceSubjectRelations(tenantSlug, id, request);
        audit.record(tenantSlug, "resources", "subject.create", "Created subject " + request.label(), Map.of("id", id));
        return subject(tenantSlug, id, false);
    }

    @Transactional
    public SubjectRecord updateSubject(String tenantSlug, UUID id, SubjectRequest request) {
        validateSubject(tenantSlug, id, request);
        int changed = jdbc.sql("""
            UPDATE subjects s SET pref_label = :label, slug = :slug, alt_labels = :aliases,
                broader_term_id = :broader, definition = :description, notation = :notation,
                is_active = :active, updated_at = CURRENT_TIMESTAMP
            FROM tenants t WHERE s.tenant_id = t.id AND t.slug = :tenantSlug AND s.id = :id
            """).param("id", id).param("tenantSlug", tenantSlug).param("label", request.label().trim())
            .param("slug", request.slug()).param("aliases", request.alternativeLabels().toArray(String[]::new))
            .param("broader", request.broaderSubjectId()).param("description", value(request.description()))
            .param("notation", nullable(request.notation())).param("active", request.active()).update();
        if (changed == 0) throw new IllegalArgumentException("Subject not found");
        replaceSubjectRelations(tenantSlug, id, request);
        audit.record(tenantSlug, "resources", "subject.update", "Updated subject " + request.label(), Map.of("id", id));
        return subject(tenantSlug, id, false);
    }

    public List<SubjectRecord> subjects(String tenantSlug, boolean publicOnly) {
        return jdbc.sql("""
            SELECT s.id FROM subjects s JOIN tenants t ON t.id = s.tenant_id
            WHERE t.slug = :tenantSlug AND (:publicOnly = false OR s.is_active = true)
            ORDER BY s.pref_label
            """).param("tenantSlug", tenantSlug).param("publicOnly", publicOnly)
            .query(UUID.class).list().stream().map(id -> subject(tenantSlug, id, publicOnly)).toList();
    }

    private SubjectRecord subject(String tenantSlug, UUID id, boolean publicOnly) {
        SubjectBase base = jdbc.sql("""
            SELECT s.id, s.pref_label, s.slug, s.alt_labels, s.definition, s.notation, s.broader_term_id,
                   broader.pref_label broader_label, s.is_active
            FROM subjects s JOIN tenants t ON t.id = s.tenant_id
            LEFT JOIN subjects broader ON broader.id = s.broader_term_id
            WHERE t.slug = :tenantSlug AND s.id = :id AND (:publicOnly = false OR s.is_active = true)
            """).param("tenantSlug", tenantSlug).param("id", id).param("publicOnly", publicOnly)
            .query((rs, n) -> new SubjectBase(rs.getObject("id", UUID.class), rs.getString("pref_label"),
                rs.getString("slug"), strings(rs.getArray("alt_labels")), rs.getString("definition"),
                rs.getString("notation"), rs.getObject("broader_term_id", UUID.class), rs.getString("broader_label"),
                rs.getBoolean("is_active"))).optional().orElseThrow(() -> new IllegalArgumentException("Subject not found"));
        List<UUID> related = ids("SELECT related_term_id FROM subject_related_terms WHERE subject_id = :id", "id", id);
        List<UUID> guides = ids("SELECT guide_id FROM guide_subjects WHERE subject_id = :id", "id", id);
        return new SubjectRecord(base.id(), base.label(), base.slug(), base.aliases(), base.description(), base.notation(),
            base.broaderId(), base.broaderLabel(), related, guides, base.active());
    }

    private void replaceRelations(String tenantSlug, UUID id, LibraryResourceRequest request) {
        jdbc.sql("DELETE FROM resource_subjects WHERE resource_id = :id").param("id", id).update();
        jdbc.sql("DELETE FROM guide_resources WHERE resource_id = :id").param("id", id).update();
        jdbc.sql("DELETE FROM reading_list_items WHERE reading_list_id = :id").param("id", id).update();
        jdbc.sql("DELETE FROM librarian_subject_liaisons WHERE librarian_id = :id").param("id", id).update();
        for (UUID subjectId : request.subjectIds()) {
            jdbc.sql("""
                INSERT INTO resource_subjects VALUES (:resourceId, :subjectId)
                """).param("resourceId", id).param("subjectId", subjectId).update();
            if (normalizeType(request.resourceType()).equals("LIBRARIAN")) {
                jdbc.sql("INSERT INTO librarian_subject_liaisons VALUES (:id, :subjectId, false)")
                    .param("id", id).param("subjectId", subjectId).update();
            }
        }
        for (int i = 0; i < request.guideIds().size(); i++) {
            jdbc.sql("INSERT INTO guide_resources (guide_id, resource_id, position) VALUES (:guideId, :id, :position)")
                .param("guideId", request.guideIds().get(i)).param("id", id).param("position", i).update();
        }
        for (int i = 0; i < request.items().size(); i++) {
            ReadingListItemInput item = request.items().get(i);
            jdbc.sql("""
                INSERT INTO reading_list_items (reading_list_id, book_id, reading_category, section_title, week_number, position, notes)
                VALUES (:id, :bookId, :category, :section, :week, :position, :notes)
                """).param("id", id).param("bookId", item.bookId())
                .param("category", blank(item.category()) ? "RECOMMENDED" : item.category().toUpperCase(Locale.ROOT))
                .param("section", nullable(item.sectionTitle())).param("week", item.weekNumber())
                .param("position", i).param("notes", value(item.notes())).update();
        }
    }

    private void replaceSubjectRelations(String tenantSlug, UUID id, SubjectRequest request) {
        jdbc.sql("DELETE FROM subject_related_terms WHERE subject_id = :id").param("id", id).update();
        jdbc.sql("DELETE FROM guide_subjects WHERE subject_id = :id").param("id", id).update();
        request.relatedSubjectIds().forEach(related -> jdbc.sql("INSERT INTO subject_related_terms VALUES (:id, :related)")
            .param("id", id).param("related", related).update());
        request.guideIds().forEach(guide -> jdbc.sql("INSERT INTO guide_subjects VALUES (:guide, :id)")
            .param("guide", guide).param("id", id).update());
    }

    private LibraryResource mapResource(java.sql.ResultSet rs) throws java.sql.SQLException {
        UUID id = rs.getObject("id", UUID.class);
        List<UUID> subjects = ids("SELECT subject_id FROM resource_subjects WHERE resource_id = :id", "id", id);
        List<UUID> guides = ids("SELECT guide_id FROM guide_resources WHERE resource_id = :id ORDER BY position", "id", id);
        List<ReadingListItemInput> items = jdbc.sql("""
            SELECT book_id, reading_category, section_title, week_number, notes
            FROM reading_list_items WHERE reading_list_id = :id ORDER BY position
            """).param("id", id).query((row, n) -> new ReadingListItemInput(row.getObject("book_id", UUID.class),
                row.getString("reading_category"), row.getString("section_title"), (Integer) row.getObject("week_number"),
                row.getString("notes"))).list();
        return new LibraryResource(id, rs.getString("resource_type"), rs.getString("title"), rs.getString("slug"),
            rs.getString("alternative_title"), rs.getString("description"), rs.getString("status"),
            rs.getString("provider"), rs.getString("publisher"), rs.getString("canonical_url"),
            rs.getString("proxy_mode"), rs.getString("access_type"), rs.getString("isbn"), strings(rs.getArray("authors")),
            rs.getString("email"), rs.getString("phone"), rs.getString("orcid"), rs.getString("faq_answer"),
            rs.getBoolean("featured"), rs.getBoolean("open_access"), rs.getObject("trial_ends_at", java.time.LocalDate.class),
            rs.getInt("helpful_votes"), rs.getInt("unhelpful_votes"),
            mapper.convertValue(JdbcJson.read(rs, "metadata", mapper), new TypeReference<>() {}), subjects, guides, items,
            rs.getLong("version"), rs.getObject("updated_at", Timestamp.class).toInstant());
    }

    private List<UUID> ids(String sql, String param, UUID id) {
        return jdbc.sql(sql).param(param, id).query(UUID.class).list();
    }

    private void validate(LibraryResourceRequest r) {
        String type = normalizeType(r.resourceType());
        if (!STATUSES.contains(r.status().toUpperCase(Locale.ROOT))) throw new IllegalArgumentException("Invalid resource status");
        if (!PROXIES.contains(r.proxyMode().toUpperCase(Locale.ROOT))) throw new IllegalArgumentException("Invalid proxy mode");
        if (type.equals("DATABASE")) validateWebUrl(r.canonicalUrl());
        if (type.equals("FAQ") && blank(r.faqAnswer())) throw new IllegalArgumentException("FAQ answer is required");
        if (!type.equals("READING_LIST") && !r.items().isEmpty()) throw new IllegalArgumentException("Only reading lists can contain books");
    }

    private void validateSubject(String tenantSlug, UUID id, SubjectRequest r) {
        if (id.equals(r.broaderSubjectId()) || r.relatedSubjectIds().contains(id)) {
            throw new IllegalArgumentException("A subject cannot relate to itself");
        }
        if (r.broaderSubjectId() != null) requireTenantSubject(tenantSlug, r.broaderSubjectId());
        r.relatedSubjectIds().forEach(subjectId -> requireTenantSubject(tenantSlug, subjectId));
        r.guideIds().forEach(guideId -> requireTenantGuide(tenantSlug, guideId));
    }

    private void validateRelations(String tenantSlug, LibraryResourceRequest request) {
        request.subjectIds().forEach(subjectId -> requireTenantSubject(tenantSlug, subjectId));
        request.guideIds().forEach(guideId -> requireTenantGuide(tenantSlug, guideId));
        for (ReadingListItemInput item : request.items()) {
            int count = jdbc.sql("""
                SELECT count(*) FROM library_resources r JOIN tenants t ON t.id = r.tenant_id
                WHERE t.slug = :tenantSlug AND r.id = :id AND r.resource_type = 'BOOK' AND r.deleted_at IS NULL
                """).param("tenantSlug", tenantSlug).param("id", item.bookId()).query(Integer.class).single();
            if (count == 0) throw new IllegalArgumentException("Reading-list book is not in this tenant");
        }
    }

    private void requireTenantSubject(String tenantSlug, UUID id) {
        int count = jdbc.sql("""
            SELECT count(*) FROM subjects s JOIN tenants t ON t.id = s.tenant_id
            WHERE t.slug = :tenantSlug AND s.id = :id
            """).param("tenantSlug", tenantSlug).param("id", id).query(Integer.class).single();
        if (count == 0) throw new IllegalArgumentException("Subject is not in this tenant");
    }

    private void requireTenantGuide(String tenantSlug, UUID id) {
        int count = jdbc.sql("""
            SELECT count(*) FROM guides g JOIN tenants t ON t.id = g.tenant_id
            WHERE t.slug = :tenantSlug AND g.id = :id AND g.deleted_at IS NULL
            """).param("tenantSlug", tenantSlug).param("id", id).query(Integer.class).single();
        if (count == 0) throw new IllegalArgumentException("Guide is not in this tenant");
    }

    private static void validateWebUrl(String value) {
        try {
            URI uri = URI.create(value);
            if (!(uri.getScheme().equals("http") || uri.getScheme().equals("https"))) throw new IllegalArgumentException();
        } catch (RuntimeException ex) { throw new IllegalArgumentException("Database URL must use HTTP or HTTPS"); }
    }

    private static String normalizeType(String type) {
        String value = type.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        if (!TYPES.contains(value)) throw new IllegalArgumentException("Unknown resource type");
        return value;
    }
    private static boolean blank(String value) { return value == null || value.isBlank(); }
    private static String nullable(String value) { return blank(value) ? null : value.trim(); }
    private static String value(String value) { return value == null ? "" : value.trim(); }
    private static List<String> strings(Array array) throws java.sql.SQLException {
        if (array == null) return List.of();
        return List.of((String[]) array.getArray());
    }
    private record SubjectBase(UUID id, String label, String slug, List<String> aliases, String description,
        String notation, UUID broaderId, String broaderLabel, boolean active) {}
}
