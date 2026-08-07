package org.grace.identity;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record IdentityProviderRequest(
    @NotBlank String providerKey,
    @NotBlank String providerType,
    @NotBlank String displayName,
    boolean enabled,
    boolean defaultProvider,
    @NotNull Object discoveryRules,
    @NotNull Object attributeMappings,
    @NotNull Object roleMappings,
    @NotNull Object configuration
) {
}
