package org.grace.users;

import java.util.List;
import java.util.UUID;

public record RoleSummary(UUID id, String name, String displayName, boolean systemRole, List<String> permissions) {}
