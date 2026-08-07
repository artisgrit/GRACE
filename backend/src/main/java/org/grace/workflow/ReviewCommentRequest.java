package org.grace.workflow;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ReviewCommentRequest(@NotBlank @Size(max = 5000) String body) {}
