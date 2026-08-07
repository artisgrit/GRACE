package org.grace.auth;

import java.util.List;
import java.util.UUID;

public record AuthenticatedUser(
    UUID id,
    String tenantSlug,
    String email,
    String displayName,
    List<String> roles,
    List<String> permissions
) {}
