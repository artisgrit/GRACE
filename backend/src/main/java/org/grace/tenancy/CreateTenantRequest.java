package org.grace.tenancy;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateTenantRequest(
    @NotBlank @Size(max = 100) @Pattern(regexp = "^[a-z0-9][a-z0-9-]{1,98}[a-z0-9]$") String slug,
    @NotBlank @Size(max = 255) String name,
    @Size(max = 255) String primaryDomain,
    @NotBlank @Size(max = 80) String timezone,
    @NotBlank @Size(max = 20) String defaultLocale
) {}
