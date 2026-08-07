package org.grace.resources;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

public record SubjectRequest(
    @NotBlank @Size(max = 255) String label,
    @NotBlank @Size(max = 300) @Pattern(regexp = "^[a-z0-9]+(?:-[a-z0-9]+)*$") String slug,
    @NotNull List<@Size(max = 255) String> alternativeLabels,
    @Size(max = 5000) String description,
    @Size(max = 50) String notation,
    UUID broaderSubjectId,
    @NotNull List<UUID> relatedSubjectIds,
    @NotNull List<UUID> guideIds,
    boolean active
) {}
