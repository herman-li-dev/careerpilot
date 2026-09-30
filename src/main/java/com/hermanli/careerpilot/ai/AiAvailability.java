package com.hermanli.careerpilot.ai;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class AiAvailability {

    private final boolean enabled;
    private final boolean clerkApplicationAuthenticationEnabled;

    @Autowired
    public AiAvailability(
            @Value("${careerpilot.ai.enabled:false}") boolean enabled,
            @Value("${careerpilot.auth.clerk-application-enabled:false}")
            boolean clerkApplicationAuthenticationEnabled
    ) {
        this.enabled = enabled;
        this.clerkApplicationAuthenticationEnabled = clerkApplicationAuthenticationEnabled;
    }

    public AiAvailability(boolean enabled) {
        this(enabled, false);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void requireEnabled() {
        requireEnabled(Operation.GENERAL);
    }

    public void requireEnabled(Operation operation) {
        if (!enabled || (clerkApplicationAuthenticationEnabled && !operation.allowedInPublicApplication())) {
            throw new AiUnavailableException();
        }
    }

    public enum Operation {
        GENERAL(false),
        GUARDED_RESUME_PARSE(true),
        GUARDED_JOB_DESCRIPTION_PARSE(true),
        GUARDED_MATCH_REPORT(true),
        GUARDED_INTERVIEW_PREPARATION(true),
        GUARDED_PLAN_REGENERATION(true),
        GUARDED_RESUME_REVIEW(true);

        private final boolean allowedInPublicApplication;

        Operation(boolean allowedInPublicApplication) {
            this.allowedInPublicApplication = allowedInPublicApplication;
        }

        boolean allowedInPublicApplication() {
            return allowedInPublicApplication;
        }
    }
}
