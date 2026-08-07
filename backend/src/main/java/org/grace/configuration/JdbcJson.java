package org.grace.configuration;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;

public final class JdbcJson {
    private JdbcJson() {
    }

    public static Object read(ResultSet rs, String column, ObjectMapper objectMapper) throws SQLException {
        try {
            String value = rs.getString(column);
            return value == null ? java.util.Map.of() : objectMapper.readValue(value, Object.class);
        } catch (Exception ex) {
            throw new SQLException("Could not parse JSON column " + column, ex);
        }
    }

    public static String write(Object value, ObjectMapper objectMapper) {
        try {
            return objectMapper.writeValueAsString(value == null ? java.util.Map.of() : value);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Invalid JSON payload", ex);
        }
    }
}
