package org.grace.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RequestCorrelationFilterTest {
    @Test
    void acceptsSafeCallerProvidedRequestId() {
        assertThat(RequestCorrelationFilter.normalize("gateway:01.request-7")).isEqualTo("gateway:01.request-7");
    }

    @Test
    void replacesUnsafeRequestId() {
        String normalized = RequestCorrelationFilter.normalize("forged\nheader");

        assertThat(normalized).doesNotContain("\n").matches("[0-9a-f-]{36}");
    }
}
