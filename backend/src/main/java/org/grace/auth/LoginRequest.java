package org.grace.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
    @NotBlank String tenantSlug,
    @Email @NotBlank String email,
    @NotBlank String password
) {}
