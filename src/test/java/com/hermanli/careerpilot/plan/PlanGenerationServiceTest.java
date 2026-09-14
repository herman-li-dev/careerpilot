package com.hermanli.careerpilot.plan;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hermanli.careerpilot.analysis.MatchReport;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlanGenerationServiceTest {

    private static final MatchReport REPORT = new MatchReport(
            50, List.of("Programming"), List.of(), List.of("Cloud Computing"),
            List.of("Java project"), List.of("No cloud evidence"), List.of("Add cloud evidence")
    );
    private static final String JOB_DESCRIPTION_JSON = """
            {"companyName":"Example","roleTitle":"Developer","location":null,
            "responsibilities":["Build APIs"],"requiredSkills":["Cloud Computing"],
            "preferredSkills":[],"experienceRequirements":[]}
            """;

    @Test
    void acceptsTasksWithCanonicalReportOrJobDescriptionEvidence() {
        PlanGenerationService service = serviceFor("""
                {"title":"14-Day Plan","summary":"Improve evidence-based gaps.","tasks":[
                {"title":"Cloud verification","description":"Verify whether a real cloud deployment exists.","dayOffset":2,"priority":"MEDIUM","sourceEvidence":"cloud computing","focusArea":"CLOUD_COMPUTING","taskType":"EVIDENCE_VERIFICATION","deliverable":"A yes/no conclusion with deployment evidence"},
                {"title":"Learn API concepts","description":"Learn API concepts and document an endpoint.","dayOffset":3,"priority":"MEDIUM","sourceEvidence":"Build APIs","focusArea":"INTEGRATION","taskType":"CONCEPT_LEARNING","deliverable":"One page of API notes with an endpoint example"}]}
                """);

        PlanDraft plan = service.generate(REPORT, JOB_DESCRIPTION_JSON);

        assertEquals("Cloud Computing", plan.tasks().get(0).sourceEvidence());
        assertEquals("HIGH", plan.tasks().get(0).priority());
        assertEquals("Build APIs", plan.tasks().get(1).sourceEvidence());
        assertEquals("LOW", plan.tasks().get(1).priority());
    }

    @Test
    void rejectsInventedTaskEvidenceBeforePersistence() {
        PlanGenerationService service = serviceFor("""
                {"title":"14-Day Plan","summary":"Improve evidence-based gaps.","tasks":[
                {"title":"Invented task","description":"Do something unsupported.","dayOffset":1,"priority":"HIGH","sourceEvidence":"Kubernetes","focusArea":"CLOUD_COMPUTING","taskType":"CONCEPT_LEARNING","deliverable":"One page of notes"}]}
                """);

        assertThrows(PlanGenerationService.InvalidPlanException.class,
                () -> service.generate(REPORT, JOB_DESCRIPTION_JSON));
    }

    @Test
    void rejectsTwoTasksForTheSameCanonicalGap() {
        PlanGenerationService service = serviceFor("""
                {"title":"14-Day Plan","summary":"Improve evidence-based gaps.","tasks":[
                {"title":"Verify cloud","description":"Verify actual cloud use.","dayOffset":1,"priority":"HIGH","sourceEvidence":"Cloud Computing","focusArea":"CLOUD_COMPUTING","taskType":"EVIDENCE_VERIFICATION","deliverable":"A yes/no conclusion"},
                {"title":"Learn cloud","description":"Learn cloud basics.","dayOffset":2,"priority":"LOW","sourceEvidence":"Add cloud evidence","focusArea":"CLOUD_COMPUTING","taskType":"CONCEPT_LEARNING","deliverable":"One page of notes"}]}
                """);

        assertThrows(PlanGenerationService.InvalidPlanException.class,
                () -> service.generate(REPORT, JOB_DESCRIPTION_JSON));
    }

    @Test
    void rejectsResumeClaimWhenEvidenceIsOnlyMissingOrRiskEvidence() {
        PlanGenerationService service = serviceFor("""
                {"title":"14-Day Plan","summary":"Improve evidence-based gaps.","tasks":[
                {"title":"Add cloud claim","description":"Add cloud experience to the resume.","dayOffset":1,"priority":"HIGH","sourceEvidence":"No cloud evidence","focusArea":"CLOUD_COMPUTING","taskType":"RESUME_APPLICATION","deliverable":"One resume bullet"}]}
                """);

        assertThrows(PlanGenerationService.InvalidPlanException.class,
                () -> service.generate(REPORT, JOB_DESCRIPTION_JSON));
    }

    @Test
    void rejectsInterviewStoryBasedOnlyOnAJobRequirement() {
        PlanGenerationService service = serviceFor("""
                {"title":"14-Day Plan","summary":"Improve evidence-based gaps.","tasks":[
                {"title":"Tell an API story","description":"Prepare a story claiming API experience.","dayOffset":1,"priority":"HIGH","sourceEvidence":"Build APIs","focusArea":"INTEGRATION","taskType":"INTERVIEW_STORY","deliverable":"One STAR story"}]}
                """);

        assertThrows(PlanGenerationService.InvalidPlanException.class,
                () -> service.generate(REPORT, JOB_DESCRIPTION_JSON));
    }

    @Test
    void rejectsCompletedOrSkippedFocusAreasDuringRegeneration() {
        PlanGenerationService service = serviceFor("""
                {"title":"14-Day Plan","summary":"Improve evidence-based gaps.","tasks":[
                {"title":"New cloud task","description":"Verify actual cloud use.","dayOffset":1,"priority":"HIGH","sourceEvidence":"Cloud Computing","focusArea":"CLOUD_COMPUTING","taskType":"EVIDENCE_VERIFICATION","deliverable":"A yes/no conclusion"}]}
                """);
        PlanTask completedCloudTask = new PlanTask(
                1L, 1L, "Earlier cloud task", "Earlier description", "COMPLETED",
                java.time.LocalDate.of(2026, 9, 1), "HIGH", "Cloud Computing",
                java.time.Instant.EPOCH, null, java.time.Instant.EPOCH, java.time.Instant.EPOCH
        );

        assertThrows(PlanGenerationService.InvalidPlanException.class,
                () -> service.generateRemaining(REPORT, JOB_DESCRIPTION_JSON, List.of(completedCloudTask), 8));
    }

    @Test
    void rejectsSkippedAndInProgressFocusAreasDuringRegeneration() {
        PlanGenerationService service = serviceFor("""
                {"title":"14-Day Plan","summary":"Improve evidence-based gaps.","tasks":[
                {"title":"New cloud task","description":"Verify actual cloud use.","dayOffset":1,"priority":"HIGH","sourceEvidence":"Cloud Computing","focusArea":"CLOUD_COMPUTING","taskType":"EVIDENCE_VERIFICATION","deliverable":"A yes/no conclusion"}]}
                """);
        PlanTask skippedCloudTask = new PlanTask(
                1L, 1L, "Skipped cloud task", "Earlier description", "SKIPPED",
                java.time.LocalDate.of(2026, 9, 1), "HIGH", "Cloud Computing",
                null, null, java.time.Instant.EPOCH, java.time.Instant.EPOCH
        );
        PlanTask inProgressCloudTask = new PlanTask(
                2L, 1L, "Cloud task in progress", "Earlier description", "IN_PROGRESS",
                java.time.LocalDate.of(2026, 9, 1), "HIGH", "Cloud Computing",
                null, null, java.time.Instant.EPOCH, java.time.Instant.EPOCH
        );

        assertThrows(PlanGenerationService.InvalidPlanException.class,
                () -> service.generateRemaining(REPORT, JOB_DESCRIPTION_JSON, List.of(skippedCloudTask), 8));
        assertThrows(PlanGenerationService.InvalidPlanException.class,
                () -> service.generateRemaining(REPORT, JOB_DESCRIPTION_JSON, List.of(inProgressCloudTask), 7));
    }

    @Test
    void suppliesOneNormalizedGapPerCanonicalFocusToTheGenerator() throws Exception {
        String generatedJson = """
                {"title":"14-Day Plan","summary":"Improve evidence-based gaps.","tasks":[
                {"title":"Cloud verification","description":"Verify actual cloud use.","dayOffset":1,"priority":"HIGH","sourceEvidence":"Cloud Computing","focusArea":"CLOUD_COMPUTING","taskType":"EVIDENCE_VERIFICATION","deliverable":"A yes/no conclusion"}]}
                """;
        RecordingPlanGenerator generator = new RecordingPlanGenerator(generatedJson);
        PlanGenerationService service = new PlanGenerationService(
                generator, new ObjectMapper(), Validation.buildDefaultValidatorFactory().getValidator()
        );
        MatchReport report = new MatchReport(
                50, List.of("Programming"), List.of(),
                List.of("Cloud Computing", "Requirements Analysis", "Test Driven Development"),
                List.of(),
                List.of("No cloud evidence", "No explicit requirements evidence"),
                List.of("Add cloud evidence", "Use requirements language", "Verify TDD before claiming it")
        );

        service.generate(report, JOB_DESCRIPTION_JSON);

        com.fasterxml.jackson.databind.JsonNode gaps = new ObjectMapper().readTree(generator.normalizedGapsJson()).path("gaps");
        com.fasterxml.jackson.databind.JsonNode request = new ObjectMapper().readTree(generator.normalizedGapsJson());
        assertEquals(gaps.size(), request.path("taskLimit").asInt());
        assertEquals(1, gaps.findValuesAsText("focusArea").stream().filter("CLOUD_COMPUTING"::equals).count());
        assertEquals(1, gaps.findValuesAsText("focusArea").stream().filter("REQUIREMENTS_ANALYSIS"::equals).count());
        assertEquals(1, gaps.findValuesAsText("focusArea").stream().filter("TDD"::equals).count());
    }

    @Test
    void rejectsTaskWithoutAConcreteDeliverable() {
        PlanGenerationService service = serviceFor("""
                {"title":"14-Day Plan","summary":"Improve evidence-based gaps.","tasks":[
                {"title":"Cloud verification","description":"Verify actual cloud use.","dayOffset":1,"priority":"HIGH","sourceEvidence":"Cloud Computing","focusArea":"CLOUD_COMPUTING","taskType":"EVIDENCE_VERIFICATION","deliverable":""}]}
                """);

        assertThrows(PlanGenerationService.InvalidPlanException.class,
                () -> service.generate(REPORT, JOB_DESCRIPTION_JSON));
    }

    @Test
    void rejectsMoreThanEightTasks() {
        String task = """
                {"title":"Cloud verification","description":"Verify actual cloud use.","dayOffset":1,"priority":"HIGH","sourceEvidence":"Cloud Computing","focusArea":"CLOUD_COMPUTING","taskType":"EVIDENCE_VERIFICATION","deliverable":"A yes/no conclusion"}
                """;
        PlanGenerationService service = serviceFor(
                "{\"title\":\"14-Day Plan\",\"summary\":\"Focused plan.\",\"tasks\":["
                        + String.join(",", Collections.nCopies(9, task)) + "]}"
        );

        assertThrows(PlanGenerationService.InvalidPlanException.class,
                () -> service.generate(REPORT, JOB_DESCRIPTION_JSON));
    }

    @Test
    void advancedProjectEvidenceSetsCapabilityFloorWithoutTreatingEmploymentGapAsMissingFundamentals() throws Exception {
        MatchReport report = new MatchReport(
                70, List.of("Programming"), List.of(), List.of(),
                List.of("Built a full-stack Spring Boot REST API with MySQL, JUnit, Playwright, and GitHub Actions"),
                List.of("No professional software development experience"), List.of("Align programming evidence to the role")
        );
        RecordingPlanGenerator generator = new RecordingPlanGenerator("""
                {"title":"Plan","summary":"Use demonstrated evidence.","tasks":[
                {"title":"Map project evidence","description":"Select the strongest implementation example.","dayOffset":1,"priority":"MEDIUM","sourceEvidence":"Built a full-stack Spring Boot REST API with MySQL, JUnit, Playwright, and GitHub Actions","focusArea":"PROGRAMMING","taskType":"RESUME_APPLICATION","deliverable":"One truthful project bullet"}]}
                """);
        PlanGenerationService service = new PlanGenerationService(
                generator, new ObjectMapper(), Validation.buildDefaultValidatorFactory().getValidator());

        service.generate(report, "{\"requiredSkills\":[\"Software development\"]}");

        com.fasterxml.jackson.databind.JsonNode gap = new ObjectMapper().readTree(generator.normalizedGapsJson())
                .path("gaps").get(0);
        assertEquals("STRONG", gap.path("evidenceStrength").asText());
        assertEquals("ADVANCED", gap.path("capabilityFloor").asText());
        assertFalse(gap.path("gapEvidence").asText().contains("professional"));
    }

    @Test
    void rejectsBeginnerProgrammingTaskWhenAdvancedEvidenceEstablishesCapabilityFloor() {
        MatchReport report = new MatchReport(
                70, List.of("Programming"), List.of(), List.of(),
                List.of("Built a Spring Boot REST API with MySQL"), List.of(), List.of("Align programming evidence")
        );
        PlanGenerationService service = serviceFor("""
                {"title":"Plan","summary":"Practice.","tasks":[
                {"title":"Programming fundamentals quiz","description":"Practice variables and loops.","dayOffset":1,"priority":"LOW","sourceEvidence":"Built a Spring Boot REST API with MySQL","focusArea":"PROGRAMMING","taskType":"EVIDENCE_VERIFICATION","deliverable":"A fundamentals quiz"}]}
                """);

        assertThrows(PlanGenerationService.InvalidPlanException.class,
                () -> service.generate(report, "{\"requiredSkills\":[\"Software development\"]}"));
    }

    @Test
    void rejectsHypotheticalInterviewStoryEvenWhenPositiveEvidenceExists() {
        MatchReport report = reportWithIntegrationEvidence("Built a Spring Boot REST API for a project");
        PlanGenerationService service = serviceFor("""
                {"title":"Plan","summary":"Prepare.","tasks":[
                {"title":"Imagine an API incident","description":"Create a hypothetical integration scenario.","dayOffset":2,"priority":"MEDIUM","sourceEvidence":"Built a Spring Boot REST API for a project","focusArea":"INTEGRATION","taskType":"INTERVIEW_STORY","deliverable":"One fictional STAR story"}]}
                """);

        assertThrows(PlanGenerationService.InvalidPlanException.class,
                () -> service.generate(report, "{\"responsibilities\":[\"Build APIs\"]}"));
    }

    @Test
    void acceptsInterviewStoryGroundedInRealPositiveEvidence() {
        MatchReport report = reportWithIntegrationEvidence("Built a Spring Boot REST API for a project");
        PlanGenerationService service = serviceFor("""
                {"title":"Plan","summary":"Prepare.","tasks":[
                {"title":"Explain the real API flow","description":"Use the implemented endpoint and service flow as evidence.","dayOffset":2,"priority":"MEDIUM","sourceEvidence":"Built a Spring Boot REST API for a project","focusArea":"INTEGRATION","taskType":"INTERVIEW_STORY","deliverable":"One evidence-grounded STAR story"}]}
                """);

        PlanDraft plan = service.generate(report, "{\"responsibilities\":[\"Build APIs\"]}");

        assertEquals("INTERVIEW_STORY", plan.tasks().get(0).taskType());
    }

    @Test
    void rejectsDemandForQuantifiedOutcomeWhenEvidenceContainsNoMetric() {
        MatchReport report = reportWithIntegrationEvidence("Built a Spring Boot REST API for a project");
        PlanGenerationService service = serviceFor("""
                {"title":"Plan","summary":"Prepare.","tasks":[
                {"title":"Quantify the API result","description":"Add a metric to the project outcome.","dayOffset":2,"priority":"MEDIUM","sourceEvidence":"Built a Spring Boot REST API for a project","focusArea":"INTEGRATION","taskType":"RESUME_APPLICATION","deliverable":"One bullet with a quantified outcome"}]}
                """);

        assertThrows(PlanGenerationService.InvalidPlanException.class,
                () -> service.generate(report, "{\"responsibilities\":[\"Build APIs\"]}"));
    }

    @Test
    void usesExistingProjectEvidenceForRequirementsMappingInsteadOfGenericLearning() {
        MatchReport report = new MatchReport(
                60, List.of(), List.of("Requirements Analysis"), List.of(),
                List.of("Mapped user needs to a workflow, data model, and authorization rules in a project"),
                List.of("Requirements evidence is not explicit"), List.of("Use requirements language")
        );
        PlanGenerationService service = serviceFor("""
                {"title":"Plan","summary":"Map evidence.","tasks":[
                {"title":"Map the implemented requirement","description":"Trace one real user need through the workflow, data model, and authorization rule.","dayOffset":1,"priority":"HIGH","sourceEvidence":"Mapped user needs to a workflow, data model, and authorization rules in a project","focusArea":"REQUIREMENTS_ANALYSIS","taskType":"INTERVIEW_STORY","deliverable":"One evidence-grounded requirement-to-implementation story"}]}
                """);

        PlanDraft plan = service.generate(report, "{\"requiredSkills\":[\"Requirements Analysis\"]}");

        assertEquals("INTERVIEW_STORY", plan.tasks().get(0).taskType());
    }

    @Test
    void usesRealTestAndDeploymentEvidenceForTroubleshootingStory() {
        MatchReport report = new MatchReport(
                60, List.of("Testing"), List.of(), List.of("Troubleshooting"),
                List.of("Resolved failing Playwright tests during a Docker deployment"),
                List.of("Troubleshooting story is not explicit"), List.of("Prepare a troubleshooting example")
        );
        PlanGenerationService service = serviceFor("""
                {"title":"Plan","summary":"Prepare real evidence.","tasks":[
                {"title":"Build a real troubleshooting story","description":"Explain the failing test, diagnosis, fix, and concrete result from the deployment.","dayOffset":1,"priority":"HIGH","sourceEvidence":"Resolved failing Playwright tests during a Docker deployment","focusArea":"TROUBLESHOOTING","taskType":"INTERVIEW_STORY","deliverable":"One evidence-grounded STAR story"}]}
                """);

        PlanDraft plan = service.generate(report, "{\"requiredSkills\":[\"Troubleshooting\"]}");

        assertEquals("HIGH", plan.tasks().get(0).priority());
    }

    @Test
    void rejectsBasicRestDefinitionWhenAppliedIntegrationEvidenceExists() {
        MatchReport report = reportWithIntegrationEvidence("Built REST API endpoints with Spring Boot controllers");
        PlanGenerationService service = serviceFor("""
                {"title":"Plan","summary":"Prepare.","tasks":[
                {"title":"REST API basics","description":"Define REST and explain what is REST.","dayOffset":1,"priority":"LOW","sourceEvidence":"Built REST API endpoints with Spring Boot controllers","focusArea":"INTEGRATION","taskType":"EVIDENCE_VERIFICATION","deliverable":"One API fundamentals page"}]}
                """);

        assertThrows(PlanGenerationService.InvalidPlanException.class,
                () -> service.generate(report, "{\"responsibilities\":[\"Build APIs\"]}"));
    }

    @Test
    void evidenceFreeGapAllowsOnlyVerificationOrLearning() throws Exception {
        String generatedJson = """
                {"title":"Plan","summary":"Verify.","tasks":[
                {"title":"Verify cloud use","description":"Check whether a real platform was used and do not claim it unless verified.","dayOffset":1,"priority":"HIGH","sourceEvidence":"Cloud Computing","focusArea":"CLOUD_COMPUTING","taskType":"EVIDENCE_VERIFICATION","deliverable":"A documented yes/no conclusion"}]}
                """;
        RecordingPlanGenerator generator = new RecordingPlanGenerator(generatedJson);
        PlanGenerationService service = new PlanGenerationService(
                generator, new ObjectMapper(), Validation.buildDefaultValidatorFactory().getValidator());

        service.generate(REPORT, JOB_DESCRIPTION_JSON);

        com.fasterxml.jackson.databind.JsonNode cloudGap = new ObjectMapper().readTree(generator.normalizedGapsJson())
                .path("gaps").findParents("focusArea").stream()
                .filter(node -> "CLOUD_COMPUTING".equals(node.path("focusArea").asText())).findFirst().orElseThrow();
        assertEquals("NONE", cloudGap.path("evidenceStrength").asText());
        assertEquals(List.of("EVIDENCE_VERIFICATION", "CONCEPT_LEARNING"),
                new ObjectMapper().convertValue(cloudGap.path("allowedTaskTypes"), List.class));
    }

    @Test
    void preservesCrossFocusSemanticGapWhenRealEvidenceWasUsedByAProtectedTask() {
        MatchReport report = new MatchReport(
                60, List.of("Testing"), List.of(), List.of("Troubleshooting"),
                List.of("Resolved failing Playwright tests during a Docker deployment"),
                List.of("Troubleshooting story is not explicit"), List.of("Prepare a troubleshooting example")
        );
        PlanTask completed = new PlanTask(
                1L, 1L, "Build a real troubleshooting story", "Explain the failure, diagnosis, and fix.", "COMPLETED",
                java.time.LocalDate.of(2026, 9, 1), "HIGH",
                "Resolved failing Playwright tests during a Docker deployment",
                java.time.Instant.EPOCH, null, java.time.Instant.EPOCH, java.time.Instant.EPOCH
        );
        PlanGenerationService service = serviceFor("""
                {"title":"Plan","summary":"Prepare.","tasks":[
                {"title":"Another troubleshooting story","description":"Explain a diagnosis and fix.","dayOffset":1,"priority":"HIGH","sourceEvidence":"Resolved failing Playwright tests during a Docker deployment","focusArea":"TROUBLESHOOTING","taskType":"INTERVIEW_STORY","deliverable":"One evidence-grounded STAR story"}]}
                """);

        assertThrows(PlanGenerationService.InvalidPlanException.class,
                () -> service.generateRemaining(report, "{\"requiredSkills\":[\"Troubleshooting\"]}",
                        List.of(completed), 7));
    }

    @Test
    void rejectsRequirementsFundamentalsWhenProjectImplementationProvidesTransferableEvidence() {
        MatchReport report = new MatchReport(
                60, List.of(), List.of(), List.of("Requirements Analysis"),
                List.of("Built application workflows with deadlines, follow-ups, REST APIs, MySQL data models, and authorization rules"),
                List.of("No formal stakeholder interview evidence"), List.of("Make requirements evidence explicit")
        );
        PlanGenerationService service = serviceFor("""
                {"title":"Plan","summary":"Prepare.","tasks":[
                {"title":"Learn Requirements Analysis Fundamentals","description":"Study stakeholder interviews, use cases, and requirements theory.","dayOffset":1,"priority":"LOW","sourceEvidence":"Requirements Analysis","focusArea":"REQUIREMENTS_ANALYSIS","taskType":"CONCEPT_LEARNING","deliverable":"One page of fundamentals notes"}]}
                """);

        assertThrows(PlanGenerationService.InvalidPlanException.class,
                () -> service.generate(report, "{\"requiredSkills\":[\"Requirements Analysis\"]}"));
    }

    @Test
    void keepsCloudVerificationAndConceptLearningActionsConsistentWithTheirTaskTypes() {
        PlanGenerationService disguisedLearning = serviceFor("""
                {"title":"Plan","summary":"Prepare.","tasks":[
                {"title":"Verify Cloud Computing Fundamentals","description":"Study virtualization, IaaS, PaaS, SaaS, and shared responsibility.","dayOffset":1,"priority":"LOW","sourceEvidence":"Cloud Computing","focusArea":"CLOUD_COMPUTING","taskType":"EVIDENCE_VERIFICATION","deliverable":"One page of cloud fundamentals notes"}]}
                """);
        PlanGenerationService honestLearning = serviceFor("""
                {"title":"Plan","summary":"Prepare.","tasks":[
                {"title":"Learn Cloud Computing Fundamentals","description":"Learn virtualization, IaaS, PaaS, SaaS, and shared responsibility.","dayOffset":1,"priority":"LOW","sourceEvidence":"Cloud Computing","focusArea":"CLOUD_COMPUTING","taskType":"CONCEPT_LEARNING","deliverable":"One page of cloud fundamentals notes"}]}
                """);

        assertThrows(PlanGenerationService.InvalidPlanException.class,
                () -> disguisedLearning.generate(REPORT, JOB_DESCRIPTION_JSON));
        assertEquals("CONCEPT_LEARNING",
                honestLearning.generate(REPORT, JOB_DESCRIPTION_JSON).tasks().get(0).taskType());
    }

    @Test
    void rejectsTechnicalIncidentDetailsThatAreAbsentFromSelectedSourceEvidence() {
        MatchReport report = troubleshootingReport("Used Docker and Postman while building REST APIs");
        List<String> unsupportedDetails = List.of(
                "Diagnose thread pools and connection leaks.",
                "Analyze Docker logs and browser dev tools.",
                "Explain inconsistent API response timing.",
                "Add a quantified improvement to the result."
        );

        unsupportedDetails.forEach(detail -> {
            PlanGenerationService service = serviceFor("""
                    {"title":"Plan","summary":"Verify.","tasks":[
                    {"title":"Identify and verify a real incident","description":"%s","dayOffset":1,"priority":"HIGH","sourceEvidence":"Used Docker and Postman while building REST APIs","focusArea":"TROUBLESHOOTING","taskType":"EVIDENCE_VERIFICATION","deliverable":"A verified incident record"}]}
                    """.formatted(detail));
            assertThrows(PlanGenerationService.InvalidPlanException.class,
                    () -> service.generate(report, "{\"requiredSkills\":[\"Troubleshooting\"]}"));
        });
    }

    @Test
    void unverifiedTroubleshootingEvidenceRequiresIncidentVerificationBeforeStoryWriting() {
        MatchReport report = troubleshootingReport("Used Docker and Postman while building REST APIs");
        PlanGenerationService verification = serviceFor("""
                {"title":"Plan","summary":"Verify.","tasks":[
                {"title":"Identify and verify one real troubleshooting incident","description":"Identify and verify one real project incident without inventing tools, causes, or results.","dayOffset":1,"priority":"HIGH","sourceEvidence":"Used Docker and Postman while building REST APIs","focusArea":"TROUBLESHOOTING","taskType":"EVIDENCE_VERIFICATION","deliverable":"One verified incident outline"}]}
                """);
        PlanGenerationService inventedStory = serviceFor("""
                {"title":"Plan","summary":"Prepare.","tasks":[
                {"title":"Write a troubleshooting story","description":"Turn the project into a troubleshooting story.","dayOffset":1,"priority":"HIGH","sourceEvidence":"Used Docker and Postman while building REST APIs","focusArea":"TROUBLESHOOTING","taskType":"INTERVIEW_STORY","deliverable":"One STAR story"}]}
                """);

        assertEquals("EVIDENCE_VERIFICATION",
                verification.generate(report, "{\"requiredSkills\":[\"Troubleshooting\"]}").tasks().get(0).taskType());
        assertThrows(PlanGenerationService.InvalidPlanException.class,
                () -> inventedStory.generate(report, "{\"requiredSkills\":[\"Troubleshooting\"]}"));
    }

    @Test
    void mapsExistingGitHubActionsWorkflowToSdlcInsteadOfAssigningFundamentals() {
        MatchReport report = new MatchReport(
                60, List.of("DevOps and Software Delivery"), List.of(), List.of("SDLC"),
                List.of("Used Git and GitHub Actions for automated test and build validation"),
                List.of("SDLC evidence is not explicit"), List.of("Connect delivery evidence to SDLC")
        );
        PlanGenerationService service = serviceFor("""
                {"title":"Plan","summary":"Map evidence.","tasks":[
                {"title":"Map existing Git and GitHub Actions workflow to SDLC","description":"Map the existing automated test and build validation workflow to development and release practices.","dayOffset":1,"priority":"MEDIUM","sourceEvidence":"Used Git and GitHub Actions for automated test and build validation","focusArea":"SDLC","taskType":"EVIDENCE_VERIFICATION","deliverable":"One workflow-to-SDLC mapping"}]}
                """);

        PlanDraft plan = service.generate(report, "{\"requiredSkills\":[\"SDLC\"]}");

        assertEquals("EVIDENCE_VERIFICATION", plan.tasks().get(0).taskType());
    }

    @Test
    void usesAuthenticTeamworkEvidenceForCollaborationStoryInsteadOfTheory() {
        MatchReport report = new MatchReport(
                65, List.of(), List.of(), List.of("Collaboration and Teamwork"),
                List.of("Worked with teammates on a group project and coordinated feature contributions"),
                List.of("Collaboration evidence is not explicit"), List.of("Prepare a collaboration example")
        );
        PlanGenerationService authenticStory = serviceFor("""
                {"title":"Plan","summary":"Prepare.","tasks":[
                {"title":"Prepare an authentic collaboration story","description":"Use the real group project and coordinated feature contributions.","dayOffset":1,"priority":"MEDIUM","sourceEvidence":"Worked with teammates on a group project and coordinated feature contributions","focusArea":"COLLABORATION","taskType":"INTERVIEW_STORY","deliverable":"One evidence-grounded STAR story"}]}
                """);
        PlanGenerationService theory = serviceFor("""
                {"title":"Plan","summary":"Study.","tasks":[
                {"title":"Learn teamwork theory","description":"Study Tuckman stages and psychological safety.","dayOffset":1,"priority":"LOW","sourceEvidence":"Collaboration and Teamwork","focusArea":"COLLABORATION","taskType":"CONCEPT_LEARNING","deliverable":"One page of theory notes"}]}
                """);

        assertEquals("INTERVIEW_STORY",
                authenticStory.generate(report, "{\"requiredSkills\":[\"Collaboration\"]}").tasks().get(0).taskType());
        assertThrows(PlanGenerationService.InvalidPlanException.class,
                () -> theory.generate(report, "{\"requiredSkills\":[\"Collaboration\"]}"));
    }

    @Test
    void reusesFullStackRestEvidenceToExplainTheActualIntegrationFlow() {
        MatchReport report = reportWithIntegrationEvidence(
                "Built a React frontend and Spring Boot REST API connected to a MySQL database");
        PlanGenerationService service = serviceFor("""
                {"title":"Plan","summary":"Map evidence.","tasks":[
                {"title":"Explain the actual integration flow","description":"Trace the real React frontend through the Spring Boot REST API to the MySQL database.","dayOffset":1,"priority":"MEDIUM","sourceEvidence":"Built a React frontend and Spring Boot REST API connected to a MySQL database","focusArea":"INTEGRATION","taskType":"INTERVIEW_STORY","deliverable":"One evidence-grounded integration walkthrough"}]}
                """);

        PlanDraft plan = service.generate(report, "{\"responsibilities\":[\"Applications Integration\"]}");

        assertEquals("INTERVIEW_STORY", plan.tasks().get(0).taskType());
    }

    @Test
    void rejectsDuplicateJsonKeysWithoutLoggingOrPersistingAmbiguousModelOutput() {
        PlanGenerationService service = serviceFor("""
                {"title":"Plan","summary":"Verify.","tasks":[
                {"title":"Verify cloud use","description":"Verify whether a real cloud platform was used.","dayOffset":1,"priority":"HIGH","sourceEvidence":"Cloud Computing","focusArea":"CLOUD_COMPUTING","taskType":"EVIDENCE_VERIFICATION","deliverable":"A yes/no conclusion","deliverable":"Duplicate value"}]}
                """);

        assertThrows(PlanGenerationService.InvalidPlanException.class,
                () -> service.generate(REPORT, JOB_DESCRIPTION_JSON));
    }

    @Test
    void acceptsAJsonCodeFenceFromThePlanModel() {
        PlanGenerationService service = serviceFor("""
                ```json
                {"title":"Plan","summary":"Verify.","tasks":[
                {"title":"Verify cloud use","description":"Verify whether a real cloud platform was used.","dayOffset":1,"priority":"HIGH","sourceEvidence":"Cloud Computing","focusArea":"CLOUD_COMPUTING","taskType":"EVIDENCE_VERIFICATION","deliverable":"A yes/no conclusion"}]}
                ```
                """);

        assertEquals("EVIDENCE_VERIFICATION",
                service.generate(REPORT, JOB_DESCRIPTION_JSON).tasks().get(0).taskType());
    }

    @Test
    void doesNotTreatAnAcademicJobRequirementAsRequirementsAnalysisEvidence() {
        assertNull(PlanGenerationService.canonicalFocusArea(
                "Academic enrollment aligns with the job's academic requirement and co-op context"));
    }

    @Test
    void replacesGenericClaimSourceWithDetailedPositiveEvidenceFromTheSameGap() {
        MatchReport report = new MatchReport(
                70, List.of("Applications Integration"), List.of(), List.of(),
                List.of("Built a React frontend and Spring Boot REST API connected to a MySQL database"),
                List.of(), List.of("Explain Applications Integration evidence")
        );
        PlanGenerationService service = serviceFor("""
                {"title":"Plan","summary":"Map evidence.","tasks":[
                {"title":"Write an integration resume bullet","description":"Describe the React frontend, Spring Boot REST API, and MySQL integration.","dayOffset":1,"priority":"MEDIUM","sourceEvidence":"Applications Integration","focusArea":"INTEGRATION","taskType":"RESUME_APPLICATION","deliverable":"One truthful integration resume bullet"}]}
                """);

        PlanDraft plan = service.generate(report, "{\"responsibilities\":[\"Applications Integration\"]}");

        assertEquals("Built a React frontend and Spring Boot REST API connected to a MySQL database",
                plan.tasks().get(0).sourceEvidence());
    }

    @Test
    void safeFallbackCreatesAtMostOneVerificationTaskPerNormalizedGap() {
        PlanGenerationService service = serviceFor("unused");

        PlanDraft fallback = service.generateSafeFallback(REPORT, JOB_DESCRIPTION_JSON);

        assertTrue(fallback.tasks().size() <= 8);
        assertEquals(fallback.tasks().size(), fallback.tasks().stream().map(PlanTaskDraft::focusArea).distinct().count());
        assertTrue(fallback.tasks().stream().allMatch(task -> "EVIDENCE_VERIFICATION".equals(task.taskType())));
        assertTrue(fallback.tasks().stream().allMatch(task -> !task.deliverable().isBlank()));
    }

    private MatchReport troubleshootingReport(String evidence) {
        return new MatchReport(60, List.of(), List.of(), List.of("Troubleshooting"),
                List.of(evidence), List.of("No verified troubleshooting incident"),
                List.of("Identify a real troubleshooting example"));
    }

    private MatchReport reportWithIntegrationEvidence(String evidence) {
        return new MatchReport(70, List.of("Applications Integration"), List.of(), List.of(),
                List.of(evidence), List.of(), List.of("Explain API integration evidence"));
    }

    private PlanGenerationService serviceFor(String generatedJson) {
        return new PlanGenerationService(
                (report, jobDescriptionParsedJson, priorTaskProgressJson) -> generatedJson,
                new ObjectMapper(),
                Validation.buildDefaultValidatorFactory().getValidator()
        );
    }

    private static class RecordingPlanGenerator implements PlanGenerator {

        private final String generatedJson;
        private String normalizedGapsJson;

        private RecordingPlanGenerator(String generatedJson) {
            this.generatedJson = generatedJson;
        }

        @Override
        public String generate(MatchReport report, String jobDescriptionParsedJson, String priorTaskProgressJson) {
            return generatedJson;
        }

        @Override
        public String generate(MatchReport report, String jobDescriptionParsedJson, String priorTaskProgressJson,
                               String normalizedGapsJson) {
            this.normalizedGapsJson = normalizedGapsJson;
            return generatedJson;
        }

        private String normalizedGapsJson() {
            return normalizedGapsJson;
        }
    }
}
