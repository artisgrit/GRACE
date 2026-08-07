package org.grace.integrations.connector;

import java.util.List;
import java.util.Map;

public record HoldingsInfo(
    String bibliographicId,
    List<AvailabilityInfo> items,
    Map<String, Object> details
) {
}
