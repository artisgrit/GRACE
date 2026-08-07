package org.grace.workflow;

import java.time.Instant;
import java.util.UUID;

public record WorkflowEvent(
    UUID id, String action, String fromStatus, String toStatus, String comment,
    String actorName, Instant createdAt
) {}
