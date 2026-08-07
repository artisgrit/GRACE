package org.grace.workflow;

import java.time.Instant;
import java.util.UUID;

public record ReviewComment(UUID id, String authorName, String body, boolean resolved, Instant createdAt) {}
