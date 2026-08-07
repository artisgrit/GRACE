package org.grace.tenancy;

import java.time.Instant;
import java.util.UUID;

public record Tenant(
    UUID id,
    String slug,
    String name,
    String primaryDomain,
    String timezone,
    String defaultLocale,
    boolean enabled,
    Instant createdAt
) {}
