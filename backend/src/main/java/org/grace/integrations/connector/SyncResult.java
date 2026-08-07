package org.grace.integrations.connector;

import java.time.Instant;
import java.util.Map;

public record SyncResult(
    boolean successful,
    long recordsProcessed,
    int errorCount,
    Instant completedAt,
    Map<String, Object> details
) {
}
