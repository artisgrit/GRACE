package org.grace.workflow;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public record WorkflowTransitionRequest(
    @NotBlank String action,
    @Size(max = 5000) String comment,
    Instant scheduledAt,
    Instant expiresAt,
    Instant nextReviewAt,
    long version
) {}
