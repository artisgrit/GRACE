package org.grace.ai;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.List;
import org.junit.jupiter.api.Test;

class AiConfigurationRequestValidationTest {
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void acceptsPrivacyPreservingLocalConfiguration() {
        AiConfigurationRequest request = new AiConfigurationRequest(
            false, "OLLAMA", "http://localhost:11434", "llama3.2", "nomic-embed-text",
            null, false, 0.2, 800, 30, 500, List.of("GUIDE", "DATABASE"), false
        );

        assertTrue(validator.validate(request).isEmpty());
    }

    @Test
    void rejectsPromptRetentionUnknownSourcesAndRawSecretValues() {
        AiConfigurationRequest request = new AiConfigurationRequest(
            true, "UNMANAGED", "https://models.example.edu", "chat", "embedding",
            "raw secret value", false, 2.5, 32, 1, 0, List.of("PRIVATE_NOTES"), true
        );

        assertFalse(validator.validate(request).isEmpty());
    }
}
