package com.hermanli.careerpilot.database;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = "spring.flyway.enabled=true")
@Tag("external")
@Transactional
class IdentitySchemaMigrationTest {

    private static final String SYNTHETIC_PASSWORD_HASH =
            "$2a$12$syntheticHashValueForSchemaTestsOnly000000000000000000";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void createsOnlyTheDocumentedUserColumns() {
        List<String> columns = jdbcTemplate.queryForList(
                """
                select column_name
                from information_schema.columns
                where table_schema = 'public' and table_name = 'app_user'
                """,
                String.class
        );

        assertEquals(Set.of(
                "id",
                "email",
                "password_hash",
                "status",
                "created_at",
                "updated_at"
        ), new HashSet<>(columns));
        assertTrue(columns.contains("password_hash"));
        assertFalse(columns.contains("password"));
    }

    @Test
    void rejectsDuplicateNormalizedEmail() {
        insertUser("candidate@example.com");

        assertThrows(DataIntegrityViolationException.class,
                () -> insertUser("candidate@example.com"));
    }

    @Test
    void rejectsEmailThatIsNotStoredInNormalizedForm() {
        assertThrows(DataIntegrityViolationException.class,
                () -> insertUser(" Candidate@Example.com "));
    }

    @Test
    void enforcesProfileOwnershipAndWeeklyHoursRange() {
        Long userId = insertUser("profile-owner@example.com");
        jdbcTemplate.update(
                "insert into user_profile (user_id, weekly_hours) values (?, ?)",
                userId,
                10
        );

        Integer profileCount = jdbcTemplate.queryForObject(
                "select count(*) from user_profile where user_id = ?",
                Integer.class,
                userId
        );

        assertEquals(1, profileCount);
    }

    @Test
    void rejectsProfileWithoutUser() {
        assertThrows(DataIntegrityViolationException.class, () -> jdbcTemplate.update(
                "insert into user_profile (user_id, weekly_hours) values (?, ?)",
                Long.MAX_VALUE,
                10
        ));
    }

    @Test
    void rejectsUnreasonableWeeklyHours() {
        Long userId = insertUser("invalid-hours@example.com");

        assertThrows(DataIntegrityViolationException.class, () -> jdbcTemplate.update(
                "insert into user_profile (user_id, weekly_hours) values (?, ?)",
                userId,
                169
        ));
    }

    private Long insertUser(String email) {
        return jdbcTemplate.queryForObject(
                "insert into app_user (email, password_hash) values (?, ?) returning id",
                Long.class,
                email,
                SYNTHETIC_PASSWORD_HASH
        );
    }
}
