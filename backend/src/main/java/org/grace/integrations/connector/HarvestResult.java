package org.grace.integrations.connector;

import java.time.Instant;
import java.util.List;

public record HarvestResult<T>(
    List<ConnectorRecord<T>> records,
    String resumptionToken,
    Instant harvestedAt
) {
}
