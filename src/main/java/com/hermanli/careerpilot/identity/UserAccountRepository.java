package com.hermanli.careerpilot.identity;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class UserAccountRepository {

    private final JdbcTemplate jdbcTemplate;

    public UserAccountRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public RegisteredUser create(String email, String passwordHash) {
        return jdbcTemplate.queryForObject(
                """
                insert into app_user (email, password_hash)
                values (?, ?)
                returning id, email, status, created_at
                """,
                (resultSet, rowNumber) -> new RegisteredUser(
                        resultSet.getLong("id"),
                        resultSet.getString("email"),
                        resultSet.getString("status"),
                        resultSet.getTimestamp("created_at").toInstant()
                ),
                email,
                passwordHash
        );
    }

    public long resolveOrCreateClerkUser(String issuer, String subject) {
        return jdbcTemplate.queryForObject(
                """
                insert into app_user (clerk_issuer, clerk_subject)
                values (?, ?)
                on conflict (clerk_issuer, clerk_subject)
                do update set clerk_subject = excluded.clerk_subject
                returning id
                """,
                Long.class,
                issuer,
                subject
        );
    }

    public Optional<StoredAccount> findByEmail(String email) {
        return jdbcTemplate.query(
                """
                select id, email, password_hash, status, created_at
                from app_user
                where email = ?
                """,
                (resultSet, rowNumber) -> new StoredAccount(
                        new RegisteredUser(
                                resultSet.getLong("id"),
                                resultSet.getString("email"),
                                resultSet.getString("status"),
                                resultSet.getTimestamp("created_at").toInstant()
                        ),
                        resultSet.getString("password_hash")
                ),
                email
        ).stream().findFirst();
    }

    public Optional<CurrentUser> findCurrentUserById(long userId) {
        return jdbcTemplate.query(
                """
                select u.id, u.email, u.status, u.created_at,
                       p.user_id as profile_user_id,
                       p.target_role, p.target_location, p.work_authorization,
                       p.weekly_hours, p.education_summary
                from app_user u
                left join user_profile p on p.user_id = u.id
                where u.id = ? and u.status = 'ACTIVE'
                """,
                (resultSet, rowNumber) -> {
                    CurrentUser.CareerProfile profile = null;
                    if (resultSet.getObject("profile_user_id") != null) {
                        profile = new CurrentUser.CareerProfile(
                                resultSet.getString("target_role"),
                                resultSet.getString("target_location"),
                                resultSet.getString("work_authorization"),
                                resultSet.getObject("weekly_hours", Integer.class),
                                resultSet.getString("education_summary")
                        );
                    }
                    return new CurrentUser(
                            resultSet.getLong("id"),
                            resultSet.getString("email"),
                            resultSet.getString("status"),
                            resultSet.getTimestamp("created_at").toInstant(),
                            profile
                    );
                },
                userId
        ).stream().findFirst();
    }

    record StoredAccount(RegisteredUser user, String passwordHash) {
    }
}
