package org.grace.guides;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record GuideDetail(
    UUID id,
    String tenantSlug,
    String title,
    String slug,
    String description,
    String guideTypeKey,
    String guideTypeName,
    String status,
    UUID ownerId,
    String ownerName,
    List<String> tags,
    Object metadata,
    String featuredImageUrl,
    String seoTitle,
    String seoDescription,
    int revisionNumber,
    long version,
    Instant createdAt,
    Instant updatedAt,
    List<GuidePage> pages
) {}
