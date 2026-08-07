package org.grace.integrations.connector;

import java.util.Collections;
import java.util.Set;

public class ConnectorCapabilities {
    private Set<String> capabilities = Collections.emptySet();

    public Set<String> getCapabilities() {
        return capabilities;
    }

    public void setCapabilities(Set<String> capabilities) {
        this.capabilities = capabilities;
    }

    public boolean supports(String capability) {
        return capabilities.contains(capability);
    }
}
