package com.hermanli.careerpilot.publicrag;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PublicRagAuthPropertiesTest {

    @Test
    void disabledConfigurationDoesNotRequireClerkValues() {
        PublicRagAuthProperties properties = new PublicRagAuthProperties();

        properties.validate();

        assertThat(properties.isEnabled()).isFalse();
    }

    @Test
    void enabledConfigurationNormalizesSafeOrigins() {
        PublicRagAuthProperties properties = new PublicRagAuthProperties();
        properties.setEnabled(true);
        properties.setIssuer("https://Example.Clerk.Accounts.dev/");
        properties.setAuthorizedParties(List.of(
                "https://CareerPilot.example.com/",
                "http://localhost:3000"
        ));

        properties.validate();

        assertThat(properties.getIssuer()).isEqualTo("https://example.clerk.accounts.dev");
        assertThat(properties.getAuthorizedParties()).containsExactly(
                "https://careerpilot.example.com",
                "http://localhost:3000"
        );
    }

    @Test
    void enabledConfigurationRejectsUnsafeOrigins() {
        PublicRagAuthProperties properties = new PublicRagAuthProperties();
        properties.setEnabled(true);
        properties.setIssuer("http://example.clerk.accounts.dev");
        properties.setAuthorizedParties(List.of("https://careerpilot.example.com/path"));

        assertThatThrownBy(properties::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Invalid Clerk origin configuration.");
    }
}
