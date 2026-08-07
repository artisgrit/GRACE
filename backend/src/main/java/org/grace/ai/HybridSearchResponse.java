package org.grace.ai;

import java.util.List;

public record HybridSearchResponse(boolean available, boolean hybrid, List<Result> results, String message) {
    public record Result(String id, String type, String title, String description, String url, double score) {}
}
