package org.grace.guides;

import java.util.List;
import java.util.UUID;

public record GuidePage(
    UUID id,
    String title,
    String slug,
    int position,
    Object layout,
    List<GuideBlock> blocks
) {}
