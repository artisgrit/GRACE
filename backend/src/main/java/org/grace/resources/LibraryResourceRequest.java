package org.grace.resources;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record LibraryResourceRequest(
    @NotBlank String resourceType,
    @NotBlank @Size(max = 500) String title,
    @NotBlank @Size(max = 300) @Pattern(regexp = "^[a-z0-9]+(?:-[a-z0-9]+)*$") String slug,
    @Size(max = 500) String alternativeTitle,
    @Size(max = 10000) String description,
    @NotBlank String status,
    @Size(max = 255) String provider,
    @Size(max = 255) String publisher,
    @Size(max = 1500) String canonicalUrl,
    @NotBlank String proxyMode,
    @Size(max = 80) String accessType,
    @Size(max = 32) String isbn,
    @NotNull List<@Size(max = 255) String> authors,
    @Email @Size(max = 320) String email,
    @Size(max = 80) String phone,
    @Size(max = 40) String orcid,
    @Size(max = 20000) String faqAnswer,
    boolean featured,
    boolean openAccess,
    LocalDate trialEndsAt,
    @NotNull Map<String, Object> metadata,
    @NotNull List<UUID> subjectIds,
    @NotNull List<UUID> guideIds,
    @NotNull List<@Valid ReadingListItemInput> items,
    long version
) {}
