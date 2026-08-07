package org.grace.guides;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

public record GuidePageInput(
    UUID id,
    @NotBlank @Size(max = 255) String title,
    @NotBlank @Size(max = 300) @Pattern(regexp = "^[a-z0-9]+(?:-[a-z0-9]+)*$") String slug,
    @NotNull Object layout,
    @NotNull List<@Valid GuideBlockInput> blocks
) {}
