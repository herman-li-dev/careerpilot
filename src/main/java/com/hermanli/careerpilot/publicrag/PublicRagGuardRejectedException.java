package com.hermanli.careerpilot.publicrag;

public class PublicRagGuardRejectedException extends RuntimeException {

    private final Reason reason;

    PublicRagGuardRejectedException(Reason reason) {
        super(reason.message);
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }

    public enum Reason {
        TOKEN_LIMIT("The public Resume AI request exceeds its token limit."),
        CONCURRENCY_LIMIT("Public Resume AI processing is busy. Please try again later."),
        USER_DAILY_LIMIT("Your daily public Resume AI limit has been reached."),
        GLOBAL_DAILY_LIMIT("The public Resume AI daily limit has been reached.");

        private final String message;

        Reason(String message) {
            this.message = message;
        }
    }
}
