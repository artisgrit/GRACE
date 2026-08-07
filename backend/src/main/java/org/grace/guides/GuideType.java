package org.grace.guides;

import java.util.UUID;

public record GuideType(UUID id, String key, String displayName, String description, boolean systemType) {}
