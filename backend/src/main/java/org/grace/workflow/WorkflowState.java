package org.grace.workflow;

import java.time.Instant;
import java.util.List;

public record WorkflowState(
    String status, long version, Instant submittedAt, Instant approvedAt, Instant scheduledAt,
    Instant publishedAt, Instant expiresAt, Instant lastReviewedAt, Instant nextReviewAt,
    Instant archivedAt, List<Reviewer> reviewers, List<ReviewComment> comments,
    List<WorkflowEvent> events
) {}
