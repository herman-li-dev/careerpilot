package com.hermanli.careerpilot.identity;

public class DuplicateEmailException extends RuntimeException {

    public DuplicateEmailException() {
        super("An account already exists for this email.");
    }
}
