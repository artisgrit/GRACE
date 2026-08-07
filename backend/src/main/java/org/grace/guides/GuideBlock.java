package org.grace.guides;

import java.util.UUID;

public record GuideBlock(UUID id, String blockType, int position, Object content, Object settings) {}
