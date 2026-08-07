package org.grace.identity;

import java.time.Instant;
import java.util.UUID;

public record IdentityProviderConfiguration(
    UUID id,
    String tenantSlug,
    String providerKey,
    String providerType,
    String displayName,
    boolean enabled,
    boolean defaultProvider,
    Object discoveryRules,
    Object attributeMappings,
    Object roleMappings,
    Object configuration,
    Instant updatedAt
) {
}
