package org.grace.workflow;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.grace.configuration.AuditService;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkflowService {
    private static final Map<String, Set<String>> SOURCES = Map.of(
        "SUBMIT", Set.of("DRAFT", "REVIEW_DUE"),
        "APPROVE", Set.of("IN_REVIEW"),
        "REJECT", Set.of("IN_REVIEW"),
        "REQUEST_CHANGES", Set.of("IN_REVIEW"),
        "SCHEDULE", Set.of("APPROVED"),
        "PUBLISH", Set.of("APPROVED", "SCHEDULED"),
        "ARCHIVE", Set.of("DRAFT", "IN_REVIEW", "APPROVED", "SCHEDULED", "PUBLISHED", "REVIEW_DUE"),
        "MARK_REVIEW_DUE", Set.of("PUBLISHED")
    );
    private static final Map<String, String> TARGETS = Map.of(
        "SUBMIT", "IN_REVIEW", "APPROVE", "APPROVED", "REJECT", "DRAFT",
        "REQUEST_CHANGES", "DRAFT", "SCHEDULE", "SCHEDULED", "PUBLISH", "PUBLISHED",
        "ARCHIVE", "ARCHIVED", "MARK_REVIEW_DUE", "REVIEW_DUE"
    );

    private final JdbcClient jdbc;
    private final AuditService audit;

    public WorkflowService(JdbcClient jdbc, AuditService audit) {
        this.jdbc = jdbc;
        this.audit = audit;
    }

    public WorkflowState state(String tenantSlug, UUID guideId) {
        Header header = header(tenantSlug, guideId);
        List<Reviewer> reviewers = jdbc.sql("""
            SELECT u.id, u.display_name, u.email, gr.assigned_at
            FROM guide_reviewers gr JOIN users u ON u.id = gr.reviewer_id
            WHERE gr.guide_id = :guideId ORDER BY u.display_name
            """).param("guideId", guideId).query((rs, n) -> new Reviewer(
                rs.getObject("id", UUID.class), rs.getString("display_name"), rs.getString("email"), instant(rs, "assigned_at")
            )).list();
        List<ReviewComment> comments = jdbc.sql("""
            SELECT c.id, u.display_name, c.body, c.resolved, c.created_at
            FROM guide_review_comments c JOIN users u ON u.id = c.author_id
            WHERE c.guide_id = :guideId ORDER BY c.created_at DESC
            """).param("guideId", guideId).query((rs, n) -> new ReviewComment(
                rs.getObject("id", UUID.class), rs.getString("display_name"), rs.getString("body"),
                rs.getBoolean("resolved"), instant(rs, "created_at")
            )).list();
        List<WorkflowEvent> events = jdbc.sql("""
            SELECT e.id, e.action, e.from_status, e.to_status, e.comment, u.display_name, e.created_at
            FROM guide_workflow_events e JOIN users u ON u.id = e.actor_id
            WHERE e.guide_id = :guideId ORDER BY e.created_at DESC
            """).param("guideId", guideId).query((rs, n) -> new WorkflowEvent(
                rs.getObject("id", UUID.class), rs.getString("action"), rs.getString("from_status"),
                rs.getString("to_status"), rs.getString("comment"), rs.getString("display_name"), instant(rs, "created_at")
            )).list();
        return new WorkflowState(header.status(), header.version(), header.submittedAt(), header.approvedAt(),
            header.scheduledAt(), header.publishedAt(), header.expiresAt(), header.lastReviewedAt(),
            header.nextReviewAt(), header.archivedAt(), reviewers, comments, events);
    }

    @Transactional
    public WorkflowState transition(String tenantSlug, UUID guideId, UUID actorId, Authentication auth, WorkflowTransitionRequest request) {
        String action = request.action().trim().toUpperCase(Locale.ROOT);
        if (!TARGETS.containsKey(action)) throw new IllegalArgumentException("Unknown workflow action");
        requirePermission(action, auth);
        Header current = header(tenantSlug, guideId);
        if (!SOURCES.get(action).contains(current.status())) {
            throw new IllegalArgumentException("Action " + action + " is not valid from " + current.status());
        }
        if ((action.equals("REJECT") || action.equals("REQUEST_CHANGES")) && blank(request.comment())) {
            throw new IllegalArgumentException("A review comment is required");
        }
        if (action.equals("SCHEDULE") && (request.scheduledAt() == null || !request.scheduledAt().isAfter(Instant.now()))) {
            throw new IllegalArgumentException("Scheduled publication must be in the future");
        }
        String target = TARGETS.get(action);
        int changed = jdbc.sql("""
            UPDATE guides SET status = :status, version = version + 1, updated_at = CURRENT_TIMESTAMP,
                submitted_at = CASE WHEN :action = 'SUBMIT' THEN CURRENT_TIMESTAMP ELSE submitted_at END,
                approved_at = CASE WHEN :action = 'APPROVE' THEN CURRENT_TIMESTAMP ELSE approved_at END,
                scheduled_at = CASE WHEN :action = 'SCHEDULE' THEN :scheduledAt ELSE scheduled_at END,
                published_at = CASE WHEN :action = 'PUBLISH' THEN CURRENT_TIMESTAMP ELSE published_at END,
                expires_at = COALESCE(:expiresAt, expires_at),
                last_reviewed_at = CASE WHEN :action IN ('APPROVE', 'PUBLISH') THEN CURRENT_TIMESTAMP ELSE last_reviewed_at END,
                next_review_at = COALESCE(:nextReviewAt, next_review_at),
                archived_at = CASE WHEN :action = 'ARCHIVE' THEN CURRENT_TIMESTAMP ELSE archived_at END
            WHERE id = :guideId AND tenant_id = (SELECT id FROM tenants WHERE slug = :tenantSlug)
              AND version = :version AND deleted_at IS NULL
            """).param("status", target).param("action", action).param("scheduledAt", timestamp(request.scheduledAt()))
            .param("expiresAt", timestamp(request.expiresAt())).param("nextReviewAt", timestamp(request.nextReviewAt()))
            .param("guideId", guideId).param("tenantSlug", tenantSlug).param("version", request.version()).update();
        if (changed == 0) throw new OptimisticLockingFailureException("The guide changed before this workflow action completed");
        jdbc.sql("""
            INSERT INTO guide_workflow_events (guide_id, actor_id, action, from_status, to_status, comment, metadata)
            VALUES (:guideId, :actorId, :action, :fromStatus, :toStatus, :comment, CAST(:metadata AS jsonb))
            """).param("guideId", guideId).param("actorId", actorId).param("action", action)
            .param("fromStatus", current.status()).param("toStatus", target)
            .param("comment", request.comment() == null ? "" : request.comment())
            .param("metadata", "{}")
            .update();
        audit.record(tenantSlug, "guides", "workflow." + action.toLowerCase(Locale.ROOT),
            "Guide moved from " + current.status() + " to " + target, Map.of("guideId", guideId, "status", target));
        return state(tenantSlug, guideId);
    }

    @Transactional
    public WorkflowState addComment(String tenantSlug, UUID guideId, UUID authorId, ReviewCommentRequest request) {
        header(tenantSlug, guideId);
        jdbc.sql("INSERT INTO guide_review_comments (guide_id, author_id, body) VALUES (:guideId, :authorId, :body)")
            .param("guideId", guideId).param("authorId", authorId).param("body", request.body().trim()).update();
        audit.record(tenantSlug, "guides", "review.comment", "Review comment added", Map.of("guideId", guideId));
        return state(tenantSlug, guideId);
    }

    @Transactional
    public WorkflowState assignReviewer(String tenantSlug, UUID guideId, UUID actorId, ReviewerRequest request) {
        header(tenantSlug, guideId);
        int valid = jdbc.sql("""
            SELECT count(*) FROM users u JOIN tenants t ON t.id = u.tenant_id
            WHERE u.id = :reviewerId AND t.slug = :tenantSlug AND u.enabled = true
            """).param("reviewerId", request.reviewerId()).param("tenantSlug", tenantSlug).query(Integer.class).single();
        if (valid == 0) throw new IllegalArgumentException("Reviewer is not an active user in this tenant");
        jdbc.sql("""
            INSERT INTO guide_reviewers (guide_id, reviewer_id, assigned_by)
            VALUES (:guideId, :reviewerId, :actorId) ON CONFLICT DO NOTHING
            """).param("guideId", guideId).param("reviewerId", request.reviewerId()).param("actorId", actorId).update();
        audit.record(tenantSlug, "guides", "reviewer.assign", "Reviewer assigned", Map.of("guideId", guideId));
        return state(tenantSlug, guideId);
    }

    private void requirePermission(String action, Authentication auth) {
        String permission = switch (action) {
            case "SUBMIT" -> "guide.edit";
            case "APPROVE", "REJECT", "REQUEST_CHANGES", "MARK_REVIEW_DUE" -> "guide.review";
            case "SCHEDULE", "PUBLISH" -> "guide.publish";
            case "ARCHIVE" -> "guide.archive";
            default -> throw new IllegalArgumentException("Unknown workflow action");
        };
        if (auth.getAuthorities().stream().noneMatch(a -> a.getAuthority().equals(permission))) {
            throw new AccessDeniedException("Missing permission " + permission);
        }
    }

    private Header header(String tenantSlug, UUID guideId) {
        return jdbc.sql("""
            SELECT g.status, g.version, g.submitted_at, g.approved_at, g.scheduled_at, g.published_at,
                   g.expires_at, g.last_reviewed_at, g.next_review_at, g.archived_at
            FROM guides g JOIN tenants t ON t.id = g.tenant_id
            WHERE t.slug = :tenantSlug AND g.id = :guideId AND g.deleted_at IS NULL
            """).param("tenantSlug", tenantSlug).param("guideId", guideId).query((rs, n) -> new Header(
                rs.getString("status"), rs.getLong("version"), instant(rs, "submitted_at"), instant(rs, "approved_at"),
                instant(rs, "scheduled_at"), instant(rs, "published_at"), instant(rs, "expires_at"),
                instant(rs, "last_reviewed_at"), instant(rs, "next_review_at"), instant(rs, "archived_at")
            )).optional().orElseThrow(() -> new IllegalArgumentException("Guide not found"));
    }

    private static Instant instant(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    private static boolean blank(String value) { return value == null || value.isBlank(); }
    private static Timestamp timestamp(Instant value) { return value == null ? null : Timestamp.from(value); }
    private record Header(String status, long version, Instant submittedAt, Instant approvedAt, Instant scheduledAt,
        Instant publishedAt, Instant expiresAt, Instant lastReviewedAt, Instant nextReviewAt, Instant archivedAt) {}
}
