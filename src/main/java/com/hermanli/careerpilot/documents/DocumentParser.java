package com.hermanli.careerpilot.documents;

/**
 * Boundary for model-backed document parsing. Implementations return model output only;
 * validation and persistence remain in {@link DocumentParsingService}.
 */
public interface DocumentParser {

    String parseResume(String rawText);

    String parseJobDescription(String rawText);
}
