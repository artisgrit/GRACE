package org.grace.guides;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record GuideSummary(
    UUID id,
    String title,
    String slug,
    String description,
    String guideTypeKey,
    String guideTypeName,
    String status,
    String ownerName,
    List<String> tags,
    int revisionNumber,
    long version,
    Instant updatedAt
) {}
