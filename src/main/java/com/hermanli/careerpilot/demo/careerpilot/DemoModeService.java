package com.hermanli.careerpilot.demo.careerpilot;

import com.hermanli.careerpilot.identity.CurrentUser;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.UUID;

@Service
@ConditionalOnProperty(prefix = "careerpilot.demo", name = "enabled", havingValue = "true")
public class DemoModeService implements ApplicationRunner {

    public static final String DEMO_EMAIL = "careerpilot-demo@example.invalid";

    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;

    public DemoModeService(JdbcTemplate jdbcTemplate, PasswordEncoder passwordEncoder) {
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        removePriorFixture();
        seedFixture();
    }

    public CurrentUser currentUser() {
        return jdbcTemplate.queryForObject(
                """
                select id, email, status, created_at
                from app_user
                where email = ? and status = 'ACTIVE'
                """,
                (resultSet, rowNumber) -> new CurrentUser(
                        resultSet.getLong("id"),
                        resultSet.getString("email"),
                        resultSet.getString("status"),
                        resultSet.getTimestamp("created_at").toInstant(),
                        null
                ),
                DEMO_EMAIL
        );
    }

    private void seedFixture() {
        long userId = jdbcTemplate.queryForObject(
                "insert into app_user (email, password_hash) values (?, ?) returning id",
                Long.class,
                DEMO_EMAIL,
                passwordEncoder.encode(UUID.randomUUID().toString())
        );
        long resumeId = jdbcTemplate.queryForObject(
                """
                insert into resume (user_id, title, raw_text, parsed_json, parse_status)
                values (?, ?, ?, cast(? as jsonb), 'COMPLETED') returning id
                """,
                Long.class,
                userId,
                "Jordan Lee — Software Developer Co-op",
                """
                Jordan Lee
                Software Development Student

                SKILLS
                Java, Spring Boot, React, TypeScript, PostgreSQL, Docker, JUnit 5, Playwright

                PROJECT
                Application Tracker — Built a full-stack application with a Spring Boot REST API, React interface,
                PostgreSQL persistence, and automated tests using JUnit 5 and Playwright.

                EDUCATION
                Associate of Science in Computer Science, expected 2027.
                """,
                """
                {
                  "skills": ["Java", "Spring Boot", "React", "TypeScript", "PostgreSQL", "Docker", "JUnit 5", "Playwright"],
                  "education": ["Associate of Science in Computer Science, expected 2027"],
                  "projects": ["Application Tracker — Built a full-stack application with a Spring Boot REST API, React interface, PostgreSQL persistence, and automated tests using JUnit 5 and Playwright."],
                  "workExperience": [],
                  "certifications": []
                }
                """
        );
        long jobDescriptionId = jdbcTemplate.queryForObject(
                """
                insert into job_description
                    (user_id, title, company_name, role_title, raw_text, parsed_json, parse_status)
                values (?, ?, ?, ?, ?, cast(? as jsonb), 'COMPLETED') returning id
                """,
                Long.class,
                userId,
                "Northstar Labs — Backend Developer Co-op",
                "Northstar Labs",
                "Backend Developer Co-op",
                """
                Northstar Labs is hiring a Backend Developer Co-op to build Java and Spring Boot services,
                collaborate with a React team, write automated tests, use PostgreSQL and Docker, and learn
                cloud deployment practices. Clear communication and requirements analysis are important.
                """,
                """
                {
                  "companyName": "Northstar Labs",
                  "roleTitle": "Backend Developer Co-op",
                  "location": "Vancouver, BC",
                  "responsibilities": ["Build Java and Spring Boot services", "Collaborate with a React team", "Write automated tests"],
                  "requiredSkills": ["Java", "Spring Boot", "PostgreSQL", "Docker", "Testing", "Communication"],
                  "preferredSkills": ["Cloud Computing", "Requirements Analysis"],
                  "experienceRequirements": ["Evidence from software projects"]
                }
                """
        );
        String reportJson = """
                {
                  "matchScore": 72,
                  "matchedSkills": ["Java", "Spring Boot", "PostgreSQL", "Docker", "Testing"],
                  "partialMatches": ["Communication"],
                  "missingSkills": ["Cloud Computing", "Requirements Analysis"],
                  "strengths": ["Full-stack project evidence", "Automated testing evidence"],
                  "risks": ["No explicit cloud deployment evidence", "Requirements analysis is not stated explicitly"],
                  "recommendations": ["Verify whether project artifacts demonstrate requirements analysis", "Prepare a truthful explanation of the Docker workflow"]
                }
                """;
        long analysisId = jdbcTemplate.queryForObject(
                """
                insert into analysis_report
                    (user_id, resume_id, job_description_id, status, match_score, report_json, started_at, completed_at)
                values (?, ?, ?, 'COMPLETED', 72, cast(? as jsonb), current_timestamp, current_timestamp)
                returning id
                """,
                Long.class,
                userId,
                resumeId,
                jobDescriptionId,
                reportJson
        );
        long planId = jdbcTemplate.queryForObject(
                """
                insert into career_plan (user_id, analysis_report_id, title, summary)
                values (?, ?, ?, ?) returning id
                """,
                Long.class,
                userId,
                analysisId,
                "14-Day Evidence Preparation Plan",
                "Prioritize verified gaps while preserving the evidence already present in the resume."
        );
        addTask(planId, "Prepare a testing walkthrough",
                "Outline how JUnit 5 and Playwright cover different test layers.",
                "COMPLETED", LocalDate.now().plusDays(1), "MEDIUM", "Automated testing evidence", true);
        addTask(planId, "Map project evidence to requirements",
                "Review existing artifacts and record only verified requirements-analysis evidence.",
                "IN_PROGRESS", LocalDate.now().plusDays(3), "HIGH", "Requirements Analysis", false);
        addTask(planId, "Document the Docker workflow",
                "Write a concise explanation using only the workflow you actually implemented.",
                "TODO", LocalDate.now().plusDays(6), "MEDIUM", "Docker", false);

        long sessionId = jdbcTemplate.queryForObject(
                "insert into interview_session (user_id, analysis_report_id, title) values (?, ?, ?) returning id",
                Long.class,
                userId,
                analysisId,
                "Evidence-Grounded Interview Preparation"
        );
        addQuestion(sessionId, 1, "TECHNICAL_GAP",
                "How would you evaluate a cloud deployment option for this Spring Boot application?",
                "Assess foundational cloud reasoning without assuming prior production deployment.",
                "Cloud Computing",
                "Separate what you know conceptually from what you have implemented.");
        addQuestion(sessionId, 2, "TECHNICAL_GAP",
                "How would you turn a user need into a testable requirement for the Application Tracker?",
                "Assess requirements-analysis thinking grounded in the named project.",
                "Requirements Analysis",
                "Use a real feature only if you can verify the original need and acceptance criteria.");
        addQuestion(sessionId, 3, "PROJECT_FOLLOW_UP",
                "How did the React interface and Spring Boot API exchange and validate data?",
                "Probe the integration evidence stated in the Resume.",
                "Application Tracker — Built a full-stack application with a Spring Boot REST API, React interface, PostgreSQL persistence, and automated tests using JUnit 5 and Playwright.",
                "Trace one verified request from the interface to persistence and back.");
        addQuestion(sessionId, 4, "PROJECT_FOLLOW_UP",
                "What did JUnit 5 and Playwright each verify in the Application Tracker?",
                "Assess whether the candidate understands the test layers they listed.",
                "Automated testing evidence",
                "Name only tests and behavior you can show in the project.");
        addQuestion(sessionId, 5, "BEHAVIORAL_EVIDENCE",
                "What real example, if any, shows how you resolved an implementation problem in this project?",
                "Invite a truthful behavioral example without manufacturing a story.",
                "Full-stack project evidence",
                "If no suitable example exists, say so and prepare a smaller verified example.");
    }

    private void addTask(long planId, String title, String description, String status, LocalDate dueDate,
                         String priority, String evidence, boolean completed) {
        jdbcTemplate.update(
                """
                insert into plan_task
                    (career_plan_id, title, description, status, due_date, priority, source_evidence, completed_at)
                values (?, ?, ?, ?, ?, ?, ?, case when ? then current_timestamp else null end)
                """,
                planId,
                title,
                description,
                status,
                dueDate,
                priority,
                evidence,
                completed
        );
    }

    private void addQuestion(long sessionId, int order, String type, String text, String goal,
                             String evidence, String tip) {
        jdbcTemplate.update(
                """
                insert into interview_question
                    (interview_session_id, question_order, question_type, question_text, assessment_goal, source_evidence, preparation_tip)
                values (?, ?, ?, ?, ?, ?, ?)
                """,
                sessionId,
                order,
                type,
                text,
                goal,
                evidence,
                tip
        );
    }

    private void removePriorFixture() {
        for (long userId : jdbcTemplate.queryForList(
                "select id from app_user where email = ?", Long.class, DEMO_EMAIL)) {
            jdbcTemplate.update("delete from interview_question where interview_session_id in (select id from interview_session where user_id = ?)", userId);
            jdbcTemplate.update("delete from interview_session where user_id = ?", userId);
            jdbcTemplate.update("delete from plan_task where career_plan_id in (select id from career_plan where user_id = ?)", userId);
            jdbcTemplate.update("delete from career_plan where user_id = ?", userId);
            jdbcTemplate.update("delete from analysis_report where user_id = ?", userId);
            jdbcTemplate.update("delete from resume where user_id = ?", userId);
            jdbcTemplate.update("delete from job_description where user_id = ?", userId);
            jdbcTemplate.update("delete from user_profile where user_id = ?", userId);
            jdbcTemplate.update("delete from app_user where id = ?", userId);
        }
    }
}
