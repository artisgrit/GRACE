package org.grace.resources;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record LibraryResource(
    UUID id, String resourceType, String title, String slug, String alternativeTitle, String description,
    String status, String provider, String publisher, String canonicalUrl, String proxyMode, String accessType,
    String isbn, List<String> authors, String email, String phone, String orcid, String faqAnswer,
    boolean featured, boolean openAccess, LocalDate trialEndsAt, int helpfulVotes, int unhelpfulVotes,
    Map<String, Object> metadata, List<UUID> subjectIds, List<UUID> guideIds,
    List<ReadingListItemInput> items, long version, Instant updatedAt
) {}
