package com.hermanli.careerpilot.publicrag;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PublicRagGuardPropertiesTest {

    @Test
    void disabledGuardDoesNotRequireASecret() {
        PublicRagGuardProperties properties = new PublicRagGuardProperties();

        assertThatNoException().isThrownBy(properties::validate);
    }

    @Test
    void enabledGuardAcceptsBoundedDefaultsAndASeparateSecret() {
        PublicRagGuardProperties properties = enabledProperties();

        assertThatNoException().isThrownBy(properties::validate);
    }

    @Test
    void enabledGuardRejectsMissingSecretAndInvalidLimits() {
        PublicRagGuardProperties missingSecret = new PublicRagGuardProperties();
        missingSecret.setEnabled(true);

        assertThatThrownBy(missingSecret::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("HMAC_SECRET");

        PublicRagGuardProperties invalidLimits = enabledProperties();
        invalidLimits.setDailyGlobalRequestLimit(2);

        assertThatThrownBy(invalidLimits::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Invalid public RAG guard limits.");
    }

    private PublicRagGuardProperties enabledProperties() {
        PublicRagGuardProperties properties = new PublicRagGuardProperties();
        properties.setEnabled(true);
        properties.setIdentityHmacSecret("synthetic-guard-secret-at-least-32-bytes");
        return properties;
    }
}
