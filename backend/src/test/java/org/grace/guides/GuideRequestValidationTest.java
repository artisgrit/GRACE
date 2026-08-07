package org.grace.guides;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class GuideRequestValidationTest {
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void rejectsInvalidCreateSlug() {
        CreateGuideRequest request = new CreateGuideRequest(
            "Research guide", "Research Guide", null, "SUBJECT", null, List.of()
        );

        assertFalse(validator.validate(request).isEmpty());
    }

    @Test
    void rejectsEmptyPageCollectionOnUpdate() {
        UpdateGuideRequest request = new UpdateGuideRequest(
            "Research guide", "research-guide", null, "SUBJECT", List.of(), Map.of(),
            null, null, null, 0, "Initial content", List.of()
        );

        assertFalse(validator.validate(request).isEmpty());
    }

    @Test
    void validatesNestedPageAndBlockContent() {
        GuidePageInput page = new GuidePageInput(
            null, "Overview", "overview", Map.of("columns", 1),
            List.of(new GuideBlockInput(null, "", Map.of(), Map.of()))
        );
        UpdateGuideRequest request = new UpdateGuideRequest(
            "Research guide", "research-guide", null, "SUBJECT", List.of("research"), Map.of(),
            null, null, null, 0, "Initial content", List.of(page)
        );

        assertTrue(validator.validate(request).stream()
            .anyMatch(violation -> violation.getPropertyPath().toString().contains("blockType")));
    }
}
