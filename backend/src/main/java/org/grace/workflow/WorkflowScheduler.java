package org.grace.workflow;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class WorkflowScheduler {
    private final JdbcClient jdbc;

    public WorkflowScheduler(JdbcClient jdbc) { this.jdbc = jdbc; }

    @Scheduled(
        initialDelayString = "${grace.workflow.scheduler-initial-delay-ms:120000}",
        fixedDelayString = "${grace.workflow.scheduler-delay-ms:60000}"
    )
    @Transactional
    public void applyDueTransitions() {
        transitionDue("SCHEDULED", "PUBLISHED", "scheduled_at", "SYSTEM_PUBLISH");
        transitionDue("PUBLISHED", "ARCHIVED", "expires_at", "SYSTEM_ARCHIVE");
        transitionDue("PUBLISHED", "REVIEW_DUE", "next_review_at", "SYSTEM_REVIEW_DUE");
    }

    private void transitionDue(String source, String target, String dateColumn, String action) {
        jdbc.sql("""
            WITH changed AS (
                UPDATE guides SET status = :target, version = version + 1, updated_at = CURRENT_TIMESTAMP,
                    published_at = CASE WHEN :target = 'PUBLISHED' THEN CURRENT_TIMESTAMP ELSE published_at END,
                    archived_at = CASE WHEN :target = 'ARCHIVED' THEN CURRENT_TIMESTAMP ELSE archived_at END
                WHERE status = :source AND %s IS NOT NULL AND %s <= CURRENT_TIMESTAMP AND deleted_at IS NULL
                RETURNING id, owner_id
            )
            INSERT INTO guide_workflow_events (guide_id, actor_id, action, from_status, to_status, comment)
            SELECT id, owner_id, :action, :source, :target, 'Automated scheduled transition' FROM changed
            """.formatted(dateColumn, dateColumn)).param("source", source).param("target", target)
            .param("action", action).update();
    }
}
