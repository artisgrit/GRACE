package org.grace.appearance;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;

public record AppearanceConfiguration(
    String tenantSlug,
    int schemaVersion,
    String activeThemeId,
    JsonNode brand,
    JsonNode tokens,
    JsonNode layout,
    JsonNode navigation,
    JsonNode components,
    String customCss,
    Instant updatedAt
) {
}
