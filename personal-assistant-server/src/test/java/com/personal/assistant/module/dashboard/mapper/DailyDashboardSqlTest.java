package com.personal.assistant.module.dashboard.mapper;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DailyDashboardSqlTest {
    @Test
    void generatedAtUsesTimestampWithoutTimeZone() {
        String sql = DailyDashboardSql.sql();

        assertTrue(sql.contains("localtimestamp generated_at"));
        assertFalse(sql.contains("now() generated_at"));
    }
}
