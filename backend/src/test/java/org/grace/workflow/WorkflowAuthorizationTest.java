package org.grace.workflow;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class WorkflowAuthorizationTest {
    @Test
    void contributorCannotPublishGuide() {
        WorkflowService service = new WorkflowService(null, null);
        WorkflowTransitionRequest request = new WorkflowTransitionRequest(
            "PUBLISH", "Attempted bypass", null, null, null, 0
        );

        assertThrows(AccessDeniedException.class, () -> service.transition(
            "default", UUID.randomUUID(), UUID.randomUUID(), contributor(), request
        ));
    }

    private JwtAuthenticationToken contributor() {
        Instant now = Instant.now();
        Jwt jwt = Jwt.withTokenValue("test-token").header("alg", "HS256").subject(UUID.randomUUID().toString())
            .issuedAt(now).expiresAt(now.plusSeconds(60)).claim("tenant", "default").build();
        return new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("guide.edit")));
    }
}
