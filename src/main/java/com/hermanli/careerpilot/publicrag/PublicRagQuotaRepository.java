package com.hermanli.careerpilot.publicrag;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Repository
class PublicRagQuotaRepository {

    private final JdbcTemplate jdbcTemplate;

    PublicRagQuotaRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public QuotaDecision reserve(
            LocalDate usageDate,
            String principalKey,
            int inputTokens,
            int outputTokens,
            int userLimit,
            int globalLimit
    ) {
        jdbcTemplate.update(
                """
                insert into public_rag_daily_usage (usage_date, scope, principal_key)
                values (?, 'GLOBAL', 'GLOBAL')
                on conflict do nothing
                """,
                usageDate
        );
        int globalCount = lockedRequestCount(usageDate, "GLOBAL", "GLOBAL");
        if (globalCount >= globalLimit) {
            return QuotaDecision.GLOBAL_LIMIT_REACHED;
        }

        jdbcTemplate.update(
                """
                insert into public_rag_daily_usage (usage_date, scope, principal_key)
                values (?, 'USER', ?)
                on conflict do nothing
                """,
                usageDate,
                principalKey
        );
        int userCount = lockedRequestCount(usageDate, "USER", principalKey);
        if (userCount >= userLimit) {
            return QuotaDecision.USER_LIMIT_REACHED;
        }

        int updatedRows = jdbcTemplate.update(
                """
                update public_rag_daily_usage
                set request_count = request_count + 1,
                    reserved_input_tokens = reserved_input_tokens + ?,
                    reserved_output_tokens = reserved_output_tokens + ?,
                    updated_at = current_timestamp
                where usage_date = ?
                  and ((scope = 'GLOBAL' and principal_key = 'GLOBAL')
                    or (scope = 'USER' and principal_key = ?))
                """,
                inputTokens,
                outputTokens,
                usageDate,
                principalKey
        );
        if (updatedRows != 2) {
            throw new IllegalStateException("Public RAG quota reservation did not update both counters.");
        }
        return QuotaDecision.ALLOWED;
    }

    private int lockedRequestCount(LocalDate usageDate, String scope, String principalKey) {
        Integer count = jdbcTemplate.queryForObject(
                """
                select request_count
                from public_rag_daily_usage
                where usage_date = ? and scope = ? and principal_key = ?
                for update
                """,
                Integer.class,
                usageDate,
                scope,
                principalKey
        );
        if (count == null) {
            throw new IllegalStateException("Public RAG quota counter was not initialized.");
        }
        return count;
    }

    enum QuotaDecision {
        ALLOWED,
        USER_LIMIT_REACHED,
        GLOBAL_LIMIT_REACHED
    }
}
