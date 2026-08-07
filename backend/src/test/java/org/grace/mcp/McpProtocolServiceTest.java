package org.grace.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.grace.guides.GuideService;
import org.grace.resources.LibraryResourceService;
import org.grace.search.SearchService;
import org.grace.semantic.SemanticService;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.oauth2.jwt.Jwt;

class McpProtocolServiceTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final McpProtocolService protocol = new McpProtocolService(
        mock(SearchService.class), mock(GuideService.class), mock(LibraryResourceService.class),
        mock(SemanticService.class), mapper, mock(JdbcClient.class), "grace-mcp"
    );

    @Test
    void initializesAndExposesOnlyReadOnlyCapabilities() {
        McpExchange initialized = protocol.handle("default", request(1, "initialize"), null, null, jwt("default"));
        assertNotNull(initialized.sessionId());
        Map<?,?> initializeBody = (Map<?,?>) initialized.body();
        assertEquals("2025-11-25", ((Map<?,?>) initializeBody.get("result")).get("protocolVersion"));

        McpExchange tools = protocol.handle("default", request(2, "tools/list"), initialized.sessionId(),
            McpProtocolService.PROTOCOL_VERSION, jwt("default"));
        String serialized = value(tools.body());
        assertFalse(serialized.contains("create_"));
        assertFalse(serialized.contains("update_"));
        assertFalse(serialized.contains("delete_"));
        assertEquals(5, mapper.valueToTree(tools.body()).path("result").path("tools").size());
    }

    @Test
    void rejectsUnsupportedProtocolMethods() {
        McpExchange initialized = protocol.handle("default", request(1, "initialize"), null, null, jwt("default"));
        McpExchange rejected = protocol.handle("default", request(2, "admin/configure"), initialized.sessionId(),
            McpProtocolService.PROTOCOL_VERSION, jwt("default"));
        assertEquals(-32601, mapper.valueToTree(rejected.body()).path("error").path("code").asInt());
    }

    private McpRequest request(int id, String method) {
        return new McpRequest("2.0", id, method, Map.of());
    }

    private Jwt jwt(String tenant) {
        Instant now = Instant.now();
        return new Jwt("token", now, now.plusSeconds(300), Map.of("alg", "HS256"),
            Map.of("sub", "f4635682-52cd-4fe4-827d-8ef8fa6f5e2e", "tenant", tenant, "aud", List.of("grace-api", "grace-mcp")));
    }

    private String value(Object object) {
        try { return mapper.writeValueAsString(object); }
        catch (Exception ex) { throw new IllegalStateException(ex); }
    }
}
