package org.grace.ai;

import jakarta.validation.constraints.*;
import java.util.List;

public record AiConfigurationRequest(
    boolean enabled,
    @NotBlank @Pattern(regexp="OLLAMA|OPENAI_COMPATIBLE") String providerType,
    @NotBlank @Size(max=1000) String baseUrl,
    @NotBlank @Size(max=255) String chatModel,
    @NotBlank @Size(max=255) String embeddingModel,
    @Size(max=120) @Pattern(regexp="[A-Za-z_][A-Za-z0-9_]*") String apiKeyEnv,
    boolean allowPrivateNetwork,
    @DecimalMin("0.0") @DecimalMax("2.0") double temperature,
    @Min(64) @Max(8192) int maxTokens,
    @Min(2) @Max(180) int timeoutSeconds,
    @Min(1) Integer monthlyRequestQuota,
    @NotEmpty List<@Pattern(regexp="GUIDE|DATABASE|BOOK|FAQ|LIBRARIAN|READING_LIST") String> approvedSources,
    @AssertFalse boolean retainPrompts
) {}
