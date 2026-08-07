package org.grace.mcp;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class McpOriginFilterTest {
    private final McpOriginFilter filter = new McpOriginFilter("https://library.example.edu,http://localhost:3000");

    @Test void acceptsConfiguredOrigins() {
        assertTrue(filter.isAllowedOrigin("https://library.example.edu/path-is-ignored"));
        assertTrue(filter.isAllowedOrigin("http://localhost:3000"));
    }

    @Test void rejectsUnconfiguredAndMalformedOrigins() {
        assertFalse(filter.isAllowedOrigin("https://attacker.example"));
        assertFalse(filter.isAllowedOrigin("not-an-origin"));
    }
}
