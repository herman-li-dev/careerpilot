package com.hermanli.careerpilot.plan;

import com.hermanli.careerpilot.analysis.AnalysisReportRepository;
import com.hermanli.careerpilot.analysis.MatchReport;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;

@Service
public class PlanPersistenceService {

    private final PlanRepository planRepository;
    private final AnalysisReportRepository analysisReportRepository;
    private final Clock clock;

    public PlanPersistenceService(
            PlanRepository planRepository,
            AnalysisReportRepository analysisReportRepository
    ) {
        this.planRepository = planRepository;
        this.analysisReportRepository = analysisReportRepository;
        this.clock = Clock.systemUTC();
    }

    @Transactional
    public long savePlanAndCompleteAnalysis(
            long userId,
            long analysisId,
            MatchReport report,
            String reportJson,
            PlanDraft plan
    ) {
        long planId = planRepository.create(userId, analysisId, plan);
        LocalDate startDate = LocalDate.now(clock);
        plan.tasks().forEach(task -> planRepository.createTask(
                planId, task, startDate.plusDays(task.dayOffset() - 1L)
        ));
        if (!analysisReportRepository.markCompleted(analysisId, userId, report, reportJson)) {
            throw new IllegalStateException("The running analysis could not be completed.");
        }
        return planId;
    }
}
