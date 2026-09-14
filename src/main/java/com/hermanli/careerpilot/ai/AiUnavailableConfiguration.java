package com.hermanli.careerpilot.ai;

import com.hermanli.careerpilot.analysis.ReportGenerator;
import com.hermanli.careerpilot.documents.DocumentParser;
import com.hermanli.careerpilot.interview.InterviewQuestionGenerator;
import com.hermanli.careerpilot.plan.PlanGenerator;
import com.hermanli.careerpilot.review.ResumeReviewGenerator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "careerpilot.ai.enabled", havingValue = "false", matchIfMissing = true)
public class AiUnavailableConfiguration {

    @Bean
    @ConditionalOnMissingBean
    DocumentParser unavailableDocumentParser() {
        return new DocumentParser() {
            @Override
            public String parseResume(String rawText) {
                throw new AiUnavailableException();
            }

            @Override
            public String parseJobDescription(String rawText) {
                throw new AiUnavailableException();
            }
        };
    }

    @Bean
    @ConditionalOnMissingBean
    ReportGenerator unavailableReportGenerator() {
        return (resumeParsedJson, jobDescriptionParsedJson) -> {
            throw new AiUnavailableException();
        };
    }

    @Bean
    @ConditionalOnMissingBean
    PlanGenerator unavailablePlanGenerator() {
        return (report, jobDescriptionParsedJson, priorTaskProgressJson) -> {
            throw new AiUnavailableException();
        };
    }

    @Bean
    @ConditionalOnMissingBean
    InterviewQuestionGenerator unavailableInterviewQuestionGenerator() {
        return evidenceContextJson -> {
            throw new AiUnavailableException();
        };
    }

    @Bean
    @ConditionalOnMissingBean
    ResumeReviewGenerator unavailableResumeReviewGenerator() {
        return request -> {
            throw new AiUnavailableException();
        };
    }
}
