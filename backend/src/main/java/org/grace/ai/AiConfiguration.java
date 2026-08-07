package org.grace.ai;

import java.time.Instant;
import java.util.List;

public record AiConfiguration(
    String tenantSlug, boolean enabled, String providerType, String baseUrl, String chatModel,
    String embeddingModel, String apiKeyEnv, boolean allowPrivateNetwork, double temperature,
    int maxTokens, int timeoutSeconds, Integer monthlyRequestQuota, List<String> approvedSources,
    boolean retainPrompts, Instant updatedAt
) {}
