package org.grace.integrations.connector;

import java.util.List;
import java.util.Map;

public record ConnectorResult<T>(
    List<T> items,
    int total,
    String nextPageToken,
    Map<String, Object> metadata
) {
}
