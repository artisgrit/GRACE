package org.grace.analytics;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Map;
import java.util.UUID;

public record AnalyticsEventRequest(
    @NotBlank @Pattern(regexp = "PAGE_VIEW|SEARCH|RESOURCE_CLICK|GUIDE_VIEW|FEEDBACK") String eventType,
    @Size(max=40) String entityType,
    UUID entityId,
    @Size(max=1000) String path,
    @Size(max=64) String sessionHash,
    Map<String,Object> metadata
) {}
