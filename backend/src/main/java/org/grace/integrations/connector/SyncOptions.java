package org.grace.integrations.connector;

import java.time.Instant;
import java.util.Map;

public record SyncOptions(
    Instant from,
    Instant until,
    boolean fullSync,
    Map<String, Object> parameters
) {
}
