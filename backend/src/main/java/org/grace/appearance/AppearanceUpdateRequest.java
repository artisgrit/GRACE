package org.grace.appearance;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AppearanceUpdateRequest(
    @NotBlank String activeThemeId,
    @NotNull Object brand,
    @NotNull Object tokens,
    @NotNull Object layout,
    @NotNull Object navigation,
    @NotNull Object components,
    String customCss
) {
}
