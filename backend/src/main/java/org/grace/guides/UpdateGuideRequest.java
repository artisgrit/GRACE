package org.grace.guides;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

public record UpdateGuideRequest(
    @NotBlank @Size(max = 255) String title,
    @NotBlank @Size(max = 300) @Pattern(regexp = "^[a-z0-9]+(?:-[a-z0-9]+)*$") String slug,
    @Size(max = 5000) String description,
    @NotBlank String guideTypeKey,
    @NotNull List<@Size(max = 80) String> tags,
    @NotNull Object metadata,
    @Size(max = 1000) String featuredImageUrl,
    @Size(max = 255) String seoTitle,
    @Size(max = 500) String seoDescription,
    long version,
    @NotBlank @Size(max = 500) String changeSummary,
    @NotEmpty List<@Valid GuidePageInput> pages
) {}
