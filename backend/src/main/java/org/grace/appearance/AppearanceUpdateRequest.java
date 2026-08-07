package org.grace.appearance;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AppearanceUpdateRequest(
    @NotBlank String activeThemeId,
    @NotNull JsonNode brand,
    @NotNull JsonNode tokens,
    @NotNull JsonNode layout,
    @NotNull JsonNode navigation,
    @NotNull JsonNode components,
    String customCss
) {
}
