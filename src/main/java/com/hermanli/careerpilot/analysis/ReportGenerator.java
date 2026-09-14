package com.hermanli.careerpilot.analysis;

/**
 * Boundary for model-backed report generation. Implementations return JSON only;
 * validation and persistence remain in {@link AnalysisReportService}.
 */
public interface ReportGenerator {

    String generate(String resumeParsedJson, String jobDescriptionParsedJson);
}
