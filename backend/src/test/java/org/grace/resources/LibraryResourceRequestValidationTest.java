package org.grace.resources;

import static org.junit.jupiter.api.Assertions.assertFalse;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class LibraryResourceRequestValidationTest {
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void rejectsInvalidSlugAndEmail() {
        LibraryResourceRequest request = new LibraryResourceRequest(
            "LIBRARIAN", "Subject librarian", "Invalid Slug", null, "Biography", "PUBLISHED",
            null, null, null, "NONE", null, null, List.of(), "not-an-email", null, null,
            null, false, false, null, Map.of(), List.of(), List.of(), List.of(), 0
        );

        assertFalse(validator.validate(request).isEmpty());
    }

    @Test
    void rejectsReadingItemWithInvalidWeek() {
        LibraryResourceRequest request = new LibraryResourceRequest(
            "READING_LIST", "Reading", "reading", null, "List", "DRAFT", null, null, null,
            "NONE", null, null, List.of(), null, null, null, null, false, false, null, Map.of(),
            List.of(), List.of(), List.of(new ReadingListItemInput(java.util.UUID.randomUUID(), "REQUIRED", null, 0, null)), 0
        );

        assertFalse(validator.validate(request).isEmpty());
    }
}
