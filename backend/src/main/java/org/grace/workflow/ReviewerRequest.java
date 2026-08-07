package org.grace.workflow;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record ReviewerRequest(@NotNull UUID reviewerId) {}
