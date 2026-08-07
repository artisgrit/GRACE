package org.grace.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@Order(1)
public class BootstrapAdministrator implements ApplicationRunner {
    private final JdbcClient jdbcClient;
    private final PasswordEncoder passwordEncoder;
    private final String email;
    private final String password;
    private final String displayName;

    public BootstrapAdministrator(
        JdbcClient jdbcClient,
        PasswordEncoder passwordEncoder,
        @Value("${grace.bootstrap.admin-email}") String email,
        @Value("${grace.bootstrap.admin-password}") String password,
        @Value("${grace.bootstrap.admin-name}") String displayName
    ) {
        this.jdbcClient = jdbcClient;
        this.passwordEncoder = passwordEncoder;
        this.email = email;
        this.password = password;
        this.displayName = displayName;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (email.isBlank() && password.isBlank()) {
            return;
        }
        if (email.isBlank() || password.length() < 12) {
            throw new IllegalStateException(
                "GRACE_BOOTSTRAP_ADMIN_EMAIL and a password of at least 12 characters must be supplied together"
            );
        }

        jdbcClient.sql("""
            INSERT INTO users (tenant_id, email, display_name, password_hash, email_verified)
            SELECT id, :email, :displayName, :passwordHash, true FROM tenants WHERE slug = 'default'
            ON CONFLICT DO NOTHING
            """)
            .param("email", email.trim().toLowerCase())
            .param("displayName", displayName)
            .param("passwordHash", passwordEncoder.encode(password))
            .update();

        jdbcClient.sql("""
            INSERT INTO user_roles (user_id, role_id)
            SELECT u.id, r.id FROM users u
            JOIN tenants t ON t.id = u.tenant_id
            JOIN roles r ON r.tenant_id = t.id AND r.name = 'SUPER_ADMIN'
            WHERE t.slug = 'default' AND LOWER(u.email) = LOWER(:email) AND u.deleted_at IS NULL
            ON CONFLICT DO NOTHING
            """)
            .param("email", email)
            .update();
    }
}
