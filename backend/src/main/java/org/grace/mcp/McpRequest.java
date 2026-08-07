package org.grace.mcp;

import jakarta.validation.constraints.NotBlank;
import java.util.Map;

public record McpRequest(String jsonrpc, Object id, @NotBlank String method, Map<String,Object> params) {}
