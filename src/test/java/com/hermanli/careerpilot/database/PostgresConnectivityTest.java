package com.hermanli.careerpilot.database;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(properties = "spring.flyway.enabled=true")
@Tag("external")
class PostgresConnectivityTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void connectsToPostgresAndAppliesBaselineMigration() {
        Integer databaseResult = jdbcTemplate.queryForObject("select 1", Integer.class);
        Integer baselineCount = jdbcTemplate.queryForObject(
                "select count(*) from flyway_schema_history where version = '1' and success",
                Integer.class
        );
        Integer failedMigrationCount = jdbcTemplate.queryForObject(
                "select count(*) from flyway_schema_history where not success",
                Integer.class
        );

        assertEquals(1, databaseResult);
        assertEquals(1, baselineCount);
        assertEquals(0, failedMigrationCount);
    }
}
