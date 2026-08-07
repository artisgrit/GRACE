package org.grace.guides;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

public record CreateGuideRequest(
    @NotBlank @Size(max = 255) String title,
    @NotBlank @Size(max = 300) @Pattern(regexp = "^[a-z0-9]+(?:-[a-z0-9]+)*$") String slug,
    @Size(max = 5000) String description,
    @NotBlank String guideTypeKey,
    String templateKey,
    List<@Size(max = 80) String> tags
) {}
