package com.hermanli.careerpilot.identity;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserAccountServiceTest {

    private final UserAccountRepository userAccountRepository = mock(UserAccountRepository.class);
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);
    private final UserAccountService service =
            new UserAccountService(userAccountRepository, passwordEncoder);

    @Test
    void normalizesEmailAndHashesPasswordBeforeStorage() {
        String suppliedPassword = UUID.randomUUID().toString();
        RegisteredUser registeredUser = new RegisteredUser(
                42L,
                "candidate@example.com",
                "ACTIVE",
                Instant.parse("2026-08-30T20:00:00Z")
        );
        when(userAccountRepository.create(anyString(), anyString())).thenReturn(registeredUser);

        assertEquals(registeredUser, service.register(" Candidate@Example.COM ", suppliedPassword));

        ArgumentCaptor<String> emailCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> hashCaptor = ArgumentCaptor.forClass(String.class);
        verify(userAccountRepository).create(emailCaptor.capture(), hashCaptor.capture());

        assertEquals("candidate@example.com", emailCaptor.getValue());
        assertNotEquals(suppliedPassword, hashCaptor.getValue());
        assertTrue(passwordEncoder.matches(suppliedPassword, hashCaptor.getValue()));
    }

    @Test
    void verifiesPasswordWithoutRevealingWhetherEmailExists() {
        String suppliedPassword = UUID.randomUUID().toString();
        String passwordHash = passwordEncoder.encode(suppliedPassword);
        RegisteredUser user = new RegisteredUser(
                42L,
                "candidate@example.com",
                "ACTIVE",
                Instant.parse("2026-08-30T20:00:00Z")
        );
        when(userAccountRepository.findByEmail("candidate@example.com"))
                .thenReturn(Optional.of(new UserAccountRepository.StoredAccount(user, passwordHash)));
        when(userAccountRepository.findByEmail("missing@example.com"))
                .thenReturn(Optional.empty());

        assertTrue(service.passwordMatches("Candidate@Example.com", suppliedPassword));
        assertFalse(service.passwordMatches("Candidate@Example.com", UUID.randomUUID().toString()));
        assertFalse(service.passwordMatches("missing@example.com", suppliedPassword));
    }

    @Test
    void mapsUniqueEmailViolationToStableDomainError() {
        String suppliedPassword = UUID.randomUUID().toString();
        when(userAccountRepository.create(anyString(), anyString()))
                .thenThrow(new DuplicateKeyException("synthetic unique violation"));

        assertThrows(DuplicateEmailException.class,
                () -> service.register("candidate@example.com", suppliedPassword));
    }
}
