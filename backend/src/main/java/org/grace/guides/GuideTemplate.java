package org.grace.guides;

import java.util.UUID;

public record GuideTemplate(
    UUID id,
    String key,
    String name,
    String description,
    String guideTypeKey,
    Object definition
) {}
