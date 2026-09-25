package com.hermanli.careerpilot.publicrag;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PublicRagGuardServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-20T12:00:00Z"), ZoneOffset.UTC);
    private static final String SUBJECT = "synthetic_clerk_subject";

    private PublicRagQuotaRepository quotaRepository;
    private PublicRagGuardProperties properties;
    private PublicRagAuthProperties authProperties;

    @BeforeEach
    void setUp() {
        quotaRepository = mock(PublicRagQuotaRepository.class);
        properties = properties(2);
        authProperties = new PublicRagAuthProperties();
        authProperties.setEnabled(true);
        when(quotaRepository.reserve(any(), anyString(), anyInt(), anyInt(), anyInt(), anyInt()))
                .thenReturn(PublicRagQuotaRepository.QuotaDecision.ALLOWED);
    }

    @Test
    void usesUtf8BytesAsAConservativeTokenizerIndependentUpperBound() {
        assertThat(PublicRagGuardService.estimateInputTokens(List.of("abcdef"))).isEqualTo(6);
        assertThat(PublicRagGuardService.estimateInputTokens(List.of("é你"))).isEqualTo(5);
    }

    @Test
    void rejectsInputAndOutputOverTokenLimitsBeforeQuotaReservation() {
        PublicRagGuardService service = service(properties);

        assertThatThrownBy(() -> service.acquire(SUBJECT, List.of("a".repeat(6_001)), 1))
                .isInstanceOfSatisfying(PublicRagGuardRejectedException.class,
                        exception -> assertThat(exception.reason())
                                .isEqualTo(PublicRagGuardRejectedException.Reason.TOKEN_LIMIT));
        assertThatThrownBy(() -> service.acquire(SUBJECT, List.of("short"), 801))
                .isInstanceOfSatisfying(PublicRagGuardRejectedException.class,
                        exception -> assertThat(exception.reason())
                                .isEqualTo(PublicRagGuardRejectedException.Reason.TOKEN_LIMIT));

        verify(quotaRepository, never()).reserve(any(), anyString(), anyInt(), anyInt(), anyInt(), anyInt());
    }

    @Test
    void rejectsConcurrentWorkWithoutQueueingAndReleasesPermitIdempotently() {
        properties = properties(1);
        PublicRagGuardService service = service(properties);

        PublicRagGuardService.GuardPermit first = service.acquire(SUBJECT, List.of("resume"), 100);
        assertThatThrownBy(() -> service.acquire("second_subject", List.of("resume"), 100))
                .isInstanceOfSatisfying(PublicRagGuardRejectedException.class,
                        exception -> assertThat(exception.reason())
                                .isEqualTo(PublicRagGuardRejectedException.Reason.CONCURRENCY_LIMIT));

        first.close();
        first.close();
        try (PublicRagGuardService.GuardPermit ignored =
                     service.acquire("second_subject", List.of("resume"), 100)) {
            assertThat(ignored.estimatedInputTokens()).isPositive();
        }
    }

    @Test
    void releasesConcurrencyWhenUserOrGlobalQuotaRejectsReservation() {
        PublicRagGuardService service = service(properties);
        when(quotaRepository.reserve(any(), anyString(), anyInt(), anyInt(), anyInt(), anyInt()))
                .thenReturn(PublicRagQuotaRepository.QuotaDecision.USER_LIMIT_REACHED)
                .thenReturn(PublicRagQuotaRepository.QuotaDecision.GLOBAL_LIMIT_REACHED)
                .thenReturn(PublicRagQuotaRepository.QuotaDecision.ALLOWED);

        assertReason(service, PublicRagGuardRejectedException.Reason.USER_DAILY_LIMIT);
        assertReason(service, PublicRagGuardRejectedException.Reason.GLOBAL_DAILY_LIMIT);
        try (PublicRagGuardService.GuardPermit ignored = service.acquire(SUBJECT, List.of("resume"), 100)) {
            assertThat(ignored.usageDate()).isEqualTo(LocalDate.of(2026, 9, 20));
        }
    }

    @Test
    void storesOnlyADateBoundHmacKeyInQuotaRepository() {
        PublicRagGuardService service = service(properties);
        ArgumentCaptor<String> principalKey = ArgumentCaptor.forClass(String.class);

        try (PublicRagGuardService.GuardPermit ignored = service.acquire(SUBJECT, List.of("resume"), 100)) {
            assertThat(ignored.reservedOutputTokens()).isEqualTo(100);
        }
        PublicRagGuardService nextDayService = new PublicRagGuardService(
                quotaRepository,
                properties,
                authProperties,
                Clock.fixed(Instant.parse("2026-09-21T12:00:00Z"), ZoneOffset.UTC)
        );
        try (PublicRagGuardService.GuardPermit ignored =
                     nextDayService.acquire(SUBJECT, List.of("resume"), 100)) {
            assertThat(ignored.usageDate()).isEqualTo(LocalDate.of(2026, 9, 21));
        }

        verify(quotaRepository, times(2)).reserve(
                any(), principalKey.capture(), anyInt(), anyInt(), anyInt(), anyInt()
        );
        assertThat(principalKey.getAllValues()).hasSize(2).doesNotHaveDuplicates();
        assertThat(principalKey.getAllValues().getFirst())
                .matches("[0-9a-f]{64}")
                .doesNotContain(SUBJECT);
    }

    @Test
    void refusesToStartWithoutPublicRagAuthentication() {
        authProperties.setEnabled(false);

        assertThatThrownBy(() -> service(properties))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("authentication");
    }

    private void assertReason(
            PublicRagGuardService service,
            PublicRagGuardRejectedException.Reason expectedReason
    ) {
        assertThatThrownBy(() -> service.acquire(SUBJECT, List.of("resume"), 100))
                .isInstanceOfSatisfying(PublicRagGuardRejectedException.class,
                        exception -> assertThat(exception.reason()).isEqualTo(expectedReason));
    }

    private PublicRagGuardService service(PublicRagGuardProperties guardProperties) {
        return new PublicRagGuardService(quotaRepository, guardProperties, authProperties, CLOCK);
    }

    private PublicRagGuardProperties properties(int concurrency) {
        PublicRagGuardProperties guardProperties = new PublicRagGuardProperties();
        guardProperties.setEnabled(true);
        guardProperties.setMaxConcurrentRequests(concurrency);
        guardProperties.setIdentityHmacSecret("synthetic-guard-secret-at-least-32-bytes");
        guardProperties.validate();
        return guardProperties;
    }
}
