package com.hermanli.careerpilot.documents;

/**
 * Safe, client-facing failures while reading an untrusted resume upload.
 */
public class ResumeUploadException extends RuntimeException {

    private final String code;

    public ResumeUploadException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
