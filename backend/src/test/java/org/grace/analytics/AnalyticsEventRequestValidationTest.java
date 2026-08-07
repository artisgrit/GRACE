package org.grace.analytics;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AnalyticsEventRequestValidationTest {
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void acceptsPrivacyLimitedSearchEvent() {
        AnalyticsEventRequest request = new AnalyticsEventRequest(
            "SEARCH", null, null, "/search", null, Map.of("type", "GUIDE")
        );
        assertTrue(validator.validate(request).isEmpty());
    }

    @Test
    void rejectsUnknownEventAndOversizedPath() {
        AnalyticsEventRequest request = new AnalyticsEventRequest(
            "USER_FINGERPRINT", null, null, "x".repeat(1001), null, Map.of()
        );
        assertFalse(validator.validate(request).isEmpty());
    }
}
