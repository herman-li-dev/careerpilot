package com.hermanli.careerpilot.ai;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class AiAvailability {

    private final boolean enabled;

    public AiAvailability(@Value("${careerpilot.ai.enabled:false}") boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void requireEnabled() {
        if (!enabled) {
            throw new AiUnavailableException();
        }
    }
}
