package org.grace.integrations.connector;

import java.time.Instant;
import java.util.Map;

public record ConnectorRecord<T>(
    String id,
    T payload,
    Instant retrievedAt,
    Map<String, Object> metadata
) {
}
