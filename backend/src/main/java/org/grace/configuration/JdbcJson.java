package org.grace.configuration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;

public final class JdbcJson {
    private JdbcJson() {
    }

    public static JsonNode read(ResultSet rs, String column, ObjectMapper objectMapper) throws SQLException {
        try {
            String value = rs.getString(column);
            return value == null ? objectMapper.createObjectNode() : objectMapper.readTree(value);
        } catch (Exception ex) {
            throw new SQLException("Could not parse JSON column " + column, ex);
        }
    }

    public static String write(JsonNode node, ObjectMapper objectMapper) {
        try {
            return objectMapper.writeValueAsString(node == null ? objectMapper.createObjectNode() : node);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Invalid JSON payload", ex);
        }
    }
}
