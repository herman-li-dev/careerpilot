package com.hermanli.careerpilot.interview;

public class InvalidInterviewQuestionException extends RuntimeException {

    private final InterviewOutputRejectionCategory category;

    public InvalidInterviewQuestionException() {
        this(InterviewOutputRejectionCategory.INVALID_JSON_STRUCTURE);
    }

    public InvalidInterviewQuestionException(InterviewOutputRejectionCategory category) {
        this.category = category;
    }

    public InterviewOutputRejectionCategory category() {
        return category;
    }
}
