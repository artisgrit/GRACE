package org.grace.appearance;

import java.time.Instant;

public record AppearanceConfiguration(
    String tenantSlug,
    int schemaVersion,
    String activeThemeId,
    Object brand,
    Object tokens,
    Object layout,
    Object navigation,
    Object components,
    String customCss,
    Instant updatedAt
) {
}
