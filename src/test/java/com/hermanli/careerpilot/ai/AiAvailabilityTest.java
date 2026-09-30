package com.hermanli.careerpilot.ai;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiAvailabilityTest {

    @Test
    void publicApplicationAllowsOnlyExplicitlyGuardedOperations() {
        AiAvailability availability = new AiAvailability(true, true);

        assertThatCode(() -> availability.requireEnabled(AiAvailability.Operation.GUARDED_RESUME_PARSE))
                .doesNotThrowAnyException();
        assertThatCode(() -> availability.requireEnabled(AiAvailability.Operation.GUARDED_JOB_DESCRIPTION_PARSE))
                .doesNotThrowAnyException();
        assertThatCode(() -> availability.requireEnabled(AiAvailability.Operation.GUARDED_MATCH_REPORT))
                .doesNotThrowAnyException();
        assertThatCode(() -> availability.requireEnabled(AiAvailability.Operation.GUARDED_INTERVIEW_PREPARATION))
                .doesNotThrowAnyException();
        assertThatCode(() -> availability.requireEnabled(AiAvailability.Operation.GUARDED_RESUME_REVIEW))
                .doesNotThrowAnyException();
        assertThatThrownBy(availability::requireEnabled).isInstanceOf(AiUnavailableException.class);
    }

    @Test
    void disabledAiRejectsGuardedOperationsToo() {
        AiAvailability availability = new AiAvailability(false, true);

        assertThatThrownBy(() -> availability.requireEnabled(AiAvailability.Operation.GUARDED_RESUME_PARSE))
                .isInstanceOf(AiUnavailableException.class);
        assertThatThrownBy(() -> availability.requireEnabled(AiAvailability.Operation.GUARDED_JOB_DESCRIPTION_PARSE))
                .isInstanceOf(AiUnavailableException.class);
        assertThatThrownBy(() -> availability.requireEnabled(AiAvailability.Operation.GUARDED_MATCH_REPORT))
                .isInstanceOf(AiUnavailableException.class);
        assertThatThrownBy(() -> availability.requireEnabled(AiAvailability.Operation.GUARDED_INTERVIEW_PREPARATION))
                .isInstanceOf(AiUnavailableException.class);
        assertThatThrownBy(() -> availability.requireEnabled(AiAvailability.Operation.GUARDED_RESUME_REVIEW))
                .isInstanceOf(AiUnavailableException.class);
    }
}
