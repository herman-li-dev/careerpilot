package com.hermanli.careerpilot.analysis;

import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "spring.flyway", name = "enabled", havingValue = "true")
class AnalysisRecovery {

    @Bean
    ApplicationRunner recoverAbandonedAnalyses(AnalysisReportRepository analysisReportRepository) {
        return arguments -> analysisReportRepository.markAbandonedAsFailed();
    }
}
