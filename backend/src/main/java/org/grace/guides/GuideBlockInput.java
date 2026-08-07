package org.grace.guides;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record GuideBlockInput(
    UUID id,
    @NotBlank String blockType,
    @NotNull Object content,
    @NotNull Object settings
) {}
