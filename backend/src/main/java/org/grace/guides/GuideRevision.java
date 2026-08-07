package org.grace.guides;

import java.time.Instant;
import java.util.UUID;

public record GuideRevision(
    UUID id,
    int revisionNumber,
    String authorName,
    String changeSummary,
    Instant createdAt
) {}
