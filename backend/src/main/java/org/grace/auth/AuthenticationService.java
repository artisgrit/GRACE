package org.grace.auth;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.stereotype.Service;

@Service
public class AuthenticationService {
    private final JdbcClient jdbcClient;
    private final PasswordEncoder passwordEncoder;
    private final JwtEncoder jwtEncoder;
    private final String issuer;
    private final long accessTokenMinutes;

    public AuthenticationService(
        JdbcClient jdbcClient,
        PasswordEncoder passwordEncoder,
        JwtEncoder jwtEncoder,
        @Value("${grace.security.jwt-issuer}") String issuer,
        @Value("${grace.security.access-token-minutes}") long accessTokenMinutes
    ) {
        this.jdbcClient = jdbcClient;
        this.passwordEncoder = passwordEncoder;
        this.jwtEncoder = jwtEncoder;
        this.issuer = issuer;
        this.accessTokenMinutes = accessTokenMinutes;
    }

    public LoginResponse login(LoginRequest request) {
        LoginUser loginUser = jdbcClient.sql("""
            SELECT u.id, t.slug AS tenant_slug, u.email, u.display_name, u.password_hash
            FROM users u JOIN tenants t ON t.id = u.tenant_id
            WHERE t.slug = :tenantSlug AND LOWER(u.email) = LOWER(:email)
              AND t.enabled = true AND u.enabled = true AND u.deleted_at IS NULL
            """)
            .param("tenantSlug", request.tenantSlug())
            .param("email", request.email())
            .query((rs, rowNum) -> new LoginUser(
                rs.getObject("id", UUID.class),
                rs.getString("tenant_slug"),
                rs.getString("email"),
                rs.getString("display_name"),
                rs.getString("password_hash")
            ))
            .optional()
            .orElseThrow(() -> new BadCredentialsException("Invalid tenant, email, or password"));

        if (!passwordEncoder.matches(request.password(), loginUser.passwordHash())) {
            throw new BadCredentialsException("Invalid tenant, email, or password");
        }

        AuthenticatedUser user = loadUser(loginUser.id(), loginUser.tenantSlug(), loginUser.email(), loginUser.displayName());
        Instant now = Instant.now();
        Instant expiresAt = now.plus(accessTokenMinutes, ChronoUnit.MINUTES);
        JwtClaimsSet claims = JwtClaimsSet.builder()
            .issuer(issuer)
            .issuedAt(now)
            .expiresAt(expiresAt)
            .subject(user.id().toString())
            .claim("tenant", user.tenantSlug())
            .claim("email", user.email())
            .claim("name", user.displayName())
            .claim("roles", user.roles())
            .claim("permissions", user.permissions())
            .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String token = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();

        jdbcClient.sql("UPDATE users SET last_login_at = CURRENT_TIMESTAMP WHERE id = :id")
            .param("id", user.id())
            .update();
        return new LoginResponse(token, expiresAt, user);
    }

    public AuthenticatedUser current(UUID userId) {
        return jdbcClient.sql("""
            SELECT u.id, t.slug AS tenant_slug, u.email, u.display_name
            FROM users u JOIN tenants t ON t.id = u.tenant_id
            WHERE u.id = :id AND u.enabled = true AND u.deleted_at IS NULL
            """)
            .param("id", userId)
            .query((rs, rowNum) -> loadUser(
                rs.getObject("id", UUID.class),
                rs.getString("tenant_slug"),
                rs.getString("email"),
                rs.getString("display_name")
            ))
            .optional()
            .orElseThrow(() -> new BadCredentialsException("User is no longer active"));
    }

    private AuthenticatedUser loadUser(UUID id, String tenantSlug, String email, String displayName) {
        List<String> roles = jdbcClient.sql("""
            SELECT r.name FROM roles r JOIN user_roles ur ON ur.role_id = r.id
            WHERE ur.user_id = :userId ORDER BY r.name
            """).param("userId", id).query(String.class).list();
        List<String> permissions = jdbcClient.sql("""
            SELECT DISTINCT p.name FROM role_permissions rp
            JOIN user_roles ur ON ur.role_id = rp.role_id
            JOIN permissions p ON p.id = rp.permission_id
            WHERE ur.user_id = :userId ORDER BY p.name
            """).param("userId", id).query(String.class).list();
        return new AuthenticatedUser(id, tenantSlug, email, displayName, roles, permissions);
    }

    private record LoginUser(UUID id, String tenantSlug, String email, String displayName, String passwordHash) {}
}
