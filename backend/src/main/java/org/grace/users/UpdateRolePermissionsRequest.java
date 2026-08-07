package org.grace.users;

import jakarta.validation.constraints.NotNull;
import java.util.Set;

public record UpdateRolePermissionsRequest(@NotNull Set<String> permissions) {}
