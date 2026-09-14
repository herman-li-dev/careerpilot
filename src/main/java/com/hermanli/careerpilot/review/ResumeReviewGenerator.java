package com.hermanli.careerpilot.review;

/**
 * Boundary for model-assisted selection among server-generated review candidates.
 * Implementations return JSON only; validation and all public suggestion fields stay server-side.
 */
public interface ResumeReviewGenerator {

    String generate(ResumeReviewModelRequest request);
}
