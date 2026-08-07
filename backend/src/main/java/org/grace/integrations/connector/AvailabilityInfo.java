package org.grace.integrations.connector;

import java.util.Map;

public record AvailabilityInfo(
    String itemId,
    String status,
    int totalCopies,
    int availableCopies,
    Map<String, Object> details
) {
}
