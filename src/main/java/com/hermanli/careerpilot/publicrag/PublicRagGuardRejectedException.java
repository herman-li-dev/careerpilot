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
        TOKEN_LIMIT("PUBLIC_RAG_TOKEN_LIMIT", "The public Resume AI request exceeds its token limit."),
        CONCURRENCY_LIMIT("PUBLIC_RAG_BUSY", "Public Resume AI processing is busy. Please try again later."),
        USER_DAILY_LIMIT("PUBLIC_RAG_USER_LIMIT", "Your daily public Resume AI limit has been reached."),
        GLOBAL_DAILY_LIMIT("PUBLIC_RAG_GLOBAL_LIMIT", "The public Resume AI daily limit has been reached.");

        private final String code;
        private final String message;

        Reason(String code, String message) {
            this.code = code;
            this.message = message;
        }

        public String code() {
            return code;
        }

        public String message() {
            return message;
        }
    }
}
