package com.hermanli.careerpilot.publicrag;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "spring.flyway.enabled=true")
@Tag("external")
class PublicRagQuotaIntegrationTest {

    private static final LocalDate TEST_DATE = LocalDate.of(2099, 1, 1);
    private static final String USER_KEY = "a".repeat(64);

    @Autowired
    private PublicRagQuotaRepository quotaRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void migrationCreatesOnlyAnonymousDailyCounterColumns() {
        assertThat(jdbcTemplate.queryForList(
                """
                select column_name
                from information_schema.columns
                where table_schema = 'public' and table_name = 'public_rag_daily_usage'
                order by column_name
                """,
                String.class
        )).containsExactly(
                "principal_key",
                "request_count",
                "reserved_input_tokens",
                "reserved_output_tokens",
                "scope",
                "updated_at",
                "usage_date"
        );
    }

    @Test
    void concurrentReservationsCannotExceedUserLimit() throws Exception {
        deleteTestRows();
        ExecutorService executor = Executors.newFixedThreadPool(8);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<PublicRagQuotaRepository.QuotaDecision>> attempts = new ArrayList<>();
            for (int index = 0; index < 12; index++) {
                attempts.add(executor.submit(() -> {
                    start.await();
                    return quotaRepository.reserve(TEST_DATE, USER_KEY, 100, 50, 3, 100);
                }));
            }
            start.countDown();

            long allowed = 0;
            for (Future<PublicRagQuotaRepository.QuotaDecision> attempt : attempts) {
                if (attempt.get() == PublicRagQuotaRepository.QuotaDecision.ALLOWED) {
                    allowed++;
                }
            }

            assertThat(allowed).isEqualTo(3);
            assertThat(requestCount("USER", USER_KEY)).isEqualTo(3);
            assertThat(requestCount("GLOBAL", "GLOBAL")).isEqualTo(3);
            assertThat(counter("USER", USER_KEY, "reserved_input_tokens")).isEqualTo(300);
            assertThat(counter("USER", USER_KEY, "reserved_output_tokens")).isEqualTo(150);
        } finally {
            executor.shutdownNow();
            deleteTestRows();
        }
    }

    @Test
    void globalLimitAppliesAcrossDifferentUsers() {
        deleteTestRows();
        try {
            assertThat(quotaRepository.reserve(TEST_DATE, "b".repeat(64), 100, 50, 3, 2))
                    .isEqualTo(PublicRagQuotaRepository.QuotaDecision.ALLOWED);
            assertThat(quotaRepository.reserve(TEST_DATE, "c".repeat(64), 100, 50, 3, 2))
                    .isEqualTo(PublicRagQuotaRepository.QuotaDecision.ALLOWED);
            assertThat(quotaRepository.reserve(TEST_DATE, "d".repeat(64), 100, 50, 3, 2))
                    .isEqualTo(PublicRagQuotaRepository.QuotaDecision.GLOBAL_LIMIT_REACHED);
            assertThat(requestCount("GLOBAL", "GLOBAL")).isEqualTo(2);
        } finally {
            deleteTestRows();
        }
    }

    private int requestCount(String scope, String principalKey) {
        return counter(scope, principalKey, "request_count");
    }

    private int counter(String scope, String principalKey, String column) {
        if (!List.of("request_count", "reserved_input_tokens", "reserved_output_tokens").contains(column)) {
            throw new IllegalArgumentException("Unsupported counter column.");
        }
        return jdbcTemplate.queryForObject(
                "select " + column + " from public_rag_daily_usage "
                        + "where usage_date = ? and scope = ? and principal_key = ?",
                Integer.class,
                TEST_DATE,
                scope,
                principalKey
        );
    }

    private void deleteTestRows() {
        jdbcTemplate.update("delete from public_rag_daily_usage where usage_date = ?", TEST_DATE);
    }
}
