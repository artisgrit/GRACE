package org.grace.resources;

import java.util.List;
import java.util.UUID;

public record SubjectRecord(
    UUID id, String label, String slug, List<String> alternativeLabels, String description,
    String notation, UUID broaderSubjectId, String broaderLabel, List<UUID> relatedSubjectIds,
    List<UUID> guideIds, boolean active
) {}
