package com.hermanli.careerpilot.interview;

public class InvalidInterviewPreparationStateException extends RuntimeException {
    public InvalidInterviewPreparationStateException() {
        super("Interview preparation requires a completed analysis with parsed inputs.");
    }
}
