package org.grace.workflow;

import java.time.Instant;
import java.util.UUID;

public record Reviewer(UUID id, String displayName, String email, Instant assignedAt) {}
