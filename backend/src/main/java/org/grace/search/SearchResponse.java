package org.grace.search;

import java.util.List;
import java.util.Map;

public record SearchResponse(
    String query,
    long total,
    List<SearchItem> results,
    Map<String, Map<String, Long>> facets,
    boolean degraded,
    String source
) {
    public record SearchItem(String id, String type, String title, String description, String url,
                             List<String> subjects, List<String> tags, double score) {}
}
