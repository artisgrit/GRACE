package org.grace.users;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.Set;

public record CreateUserRequest(
    @Email @NotBlank @Size(max = 320) String email,
    @NotBlank @Size(max = 255) String displayName,
    @NotBlank @Size(min = 12, max = 200) String password,
    @NotEmpty Set<String> roles
) {}
