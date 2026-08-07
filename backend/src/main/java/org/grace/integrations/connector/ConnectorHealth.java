package org.grace.integrations.connector;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

public class ConnectorHealth {
    private boolean enabled;
    private String status;
    private Instant lastSuccessfulConnection;
    private Instant lastErrorTime;
    private String lastErrorMessage;
    private Instant lastSync;
    private Instant nextScheduledSync;
    private long recordsProcessed;
    private int errorCount;
    private Map<String, Object> details;

    public ConnectorHealth() {
        this.enabled = false;
        this.status = "unknown";
        this.details = new HashMap<>();
    }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Instant getLastSuccessfulConnection() { return lastSuccessfulConnection; }
    public void setLastSuccessfulConnection(Instant t) { this.lastSuccessfulConnection = t; }
    public Instant getLastErrorTime() { return lastErrorTime; }
    public void setLastErrorTime(Instant t) { this.lastErrorTime = t; }
    public String getLastErrorMessa
