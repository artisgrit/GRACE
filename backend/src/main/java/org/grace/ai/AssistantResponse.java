package org.grace.ai;

import java.util.List;

public record AssistantResponse(
    boolean available, String status, String answer, List<Citation> citations,
    boolean hybridRetrieval, String message
) {
    public record Citation(int number, String type, String title, String url) {}
}
