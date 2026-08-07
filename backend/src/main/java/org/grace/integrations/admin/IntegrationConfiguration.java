package org.grace.integrations.admin;

import java.time.Instant;
import java.util.UUID;

public record IntegrationConfiguration(
    UUID id,
    String tenantSlug,
    String connectorKey,
    String connectorType,
    String displayName,
    boolean enabled,
    Object configurationSchema,
    Object configuration,
    Object mappingProfile,
    Object schedule,
    Object health,
    Instant updatedAt
) {
}
