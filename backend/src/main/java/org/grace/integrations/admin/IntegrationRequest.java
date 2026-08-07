package org.grace.integrations.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record IntegrationRequest(
    @NotBlank String connectorKey,
    @NotBlank String connectorType,
    @NotBlank String displayName,
    boolean enabled,
    @NotNull Object configurationSchema,
    @NotNull Object configuration,
    @NotNull Object mappingProfile,
    @NotNull Object schedule,
    @NotNull Object health
) {
}
