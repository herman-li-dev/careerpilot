package com.hermanli.careerpilot.ai;

public class AiUnavailableException extends RuntimeException {

    public AiUnavailableException() {
        super("AI features are not enabled for this environment.");
    }
}
