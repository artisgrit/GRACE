package org.grace.integrations.admin;

import java.util.List;

public record ConnectorCatalogueItem(
    String connectorType,
    String label,
    List<String> protocols,
    List<String> capabilities
) {
}
