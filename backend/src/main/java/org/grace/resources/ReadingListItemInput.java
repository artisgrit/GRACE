package org.grace.resources;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record ReadingListItemInput(
    @NotNull UUID bookId,
    @Size(max = 40) String category,
    @Size(max = 255) String sectionTitle,
    @Min(1) Integer weekNumber,
    @Size(max = 5000) String notes
) {}
