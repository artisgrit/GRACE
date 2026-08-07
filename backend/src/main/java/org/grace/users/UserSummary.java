package org.grace.users;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record UserSummary(
    UUID id,
    String email,
    String displayName,
    boolean enabled,
    boolean emailVerified,
    Instant lastLoginAt,
    Instant createdAt,
    List<String> roles
) {}
