package org.grace.configuration;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.UUID;

public record AuditEvent(
    UUID id,
    String tenantSlug,
    String actor,
    String area,
    String action,
    String summary,
    JsonNode payload,
    Instant createdAt
) {
}
