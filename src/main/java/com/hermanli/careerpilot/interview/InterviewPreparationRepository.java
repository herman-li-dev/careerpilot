package com.hermanli.careerpilot.interview;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public class InterviewPreparationRepository {

    private final JdbcTemplate jdbcTemplate;

    public InterviewPreparationRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<InterviewSessionView> findByAnalysisReportIdAndUserId(long analysisReportId, long userId) {
        return jdbcTemplate.query(
                """
                select id, analysis_report_id, title, created_at
                from interview_session where analysis_report_id = ? and user_id = ?
                """,
                (rs, rowNum) -> session(rs.getLong("id"), rs.getLong("analysis_report_id"), rs.getString("title"),
                        rs.getTimestamp("created_at").toInstant()),
                analysisReportId, userId
        ).stream().findFirst();
    }

    public Optional<InterviewSessionView> findByIdAndUserId(long sessionId, long userId) {
        return jdbcTemplate.query(
                "select id, analysis_report_id, title, created_at from interview_session where id = ? and user_id = ?",
                (rs, rowNum) -> session(rs.getLong("id"), rs.getLong("analysis_report_id"), rs.getString("title"),
                        rs.getTimestamp("created_at").toInstant()),
                sessionId, userId
        ).stream().findFirst();
    }

    @Transactional
    public InterviewSessionView create(long userId, long analysisReportId, String title, InterviewPreparationDraft draft) {
        long sessionId = jdbcTemplate.queryForObject(
                """
                insert into interview_session (user_id, analysis_report_id, title) values (?, ?, ?) returning id
                """, Long.class, userId, analysisReportId, title
        );
        for (int index = 0; index < draft.questions().size(); index++) {
            InterviewQuestionDraft question = draft.questions().get(index);
            jdbcTemplate.update(
                    """
                    insert into interview_question
                        (interview_session_id, question_order, question_type, question_text, assessment_goal, source_evidence, preparation_tip)
                    values (?, ?, ?, ?, ?, ?, ?)
                    """,
                    sessionId, index + 1, question.questionType().name(), question.questionText(), question.assessmentGoal(),
                    question.sourceEvidence(), question.preparationTip()
            );
        }
        return findByIdAndUserId(sessionId, userId).orElseThrow();
    }

    private InterviewSessionView session(long id, long analysisReportId, String title, Instant createdAt) {
        List<InterviewQuestion> questions = jdbcTemplate.query(
                """
                select id, question_order, question_type, question_text, assessment_goal, source_evidence, preparation_tip, created_at
                from interview_question where interview_session_id = ? order by question_order
                """,
                (rs, rowNum) -> new InterviewQuestion(rs.getLong("id"), rs.getShort("question_order"),
                        InterviewQuestionType.valueOf(rs.getString("question_type")), rs.getString("question_text"),
                        rs.getString("assessment_goal"), rs.getString("source_evidence"), rs.getString("preparation_tip"),
                        rs.getTimestamp("created_at").toInstant()),
                id
        );
        return new InterviewSessionView(id, analysisReportId, title, createdAt, questions);
    }
}
