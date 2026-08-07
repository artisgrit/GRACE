package org.grace.auth;

import java.time.Instant;

public record LoginResponse(String accessToken, Instant expiresAt, AuthenticatedUser user) {}
