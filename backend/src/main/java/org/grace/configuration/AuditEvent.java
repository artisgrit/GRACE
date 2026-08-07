package org.grace.configuration;

import java.time.Instant;
import java.util.UUID;

public record AuditEvent(
    UUID id,
    String tenantSlug,
    String actor,
    String area,
    String action,
    String summary,
    Object payload,
    Instant createdAt
) {
}
