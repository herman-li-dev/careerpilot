package com.hermanli.careerpilot.identity;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Service
public class UserAccountService {

    private final UserAccountRepository userAccountRepository;
    private final PasswordEncoder passwordEncoder;
    private final String missingAccountPasswordHash;

    public UserAccountService(
            UserAccountRepository userAccountRepository,
            PasswordEncoder passwordEncoder
    ) {
        this.userAccountRepository = userAccountRepository;
        this.passwordEncoder = passwordEncoder;
        this.missingAccountPasswordHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    @Transactional
    public RegisteredUser register(String email, String password) {
        String normalizedEmail = normalizeEmail(email);
        String passwordHash = passwordEncoder.encode(password);

        try {
            return userAccountRepository.create(normalizedEmail, passwordHash);
        } catch (DuplicateKeyException exception) {
            throw new DuplicateEmailException();
        }
    }

    @Transactional(readOnly = true)
    public Optional<RegisteredUser> authenticate(String email, String password) {
        Optional<UserAccountRepository.StoredAccount> storedAccount =
                userAccountRepository.findByEmail(normalizeEmail(email));
        String passwordHash = storedAccount
                .map(UserAccountRepository.StoredAccount::passwordHash)
                .orElse(missingAccountPasswordHash);
        boolean passwordMatches = passwordEncoder.matches(password, passwordHash);

        if (!passwordMatches || storedAccount.isEmpty()) {
            return Optional.empty();
        }

        RegisteredUser user = storedAccount.orElseThrow().user();
        return "ACTIVE".equals(user.status()) ? Optional.of(user) : Optional.empty();
    }

    @Transactional(readOnly = true)
    public boolean passwordMatches(String email, String password) {
        return authenticate(email, password).isPresent();
    }

    @Transactional(readOnly = true)
    public Optional<CurrentUser> findCurrentUser(long userId) {
        return userAccountRepository.findCurrentUserById(userId);
    }

    @Transactional
    public long resolveOrCreateClerkUser(String issuer, String subject) {
        if (issuer == null || issuer.isBlank() || subject == null || subject.isBlank()) {
            throw new AuthenticationRequiredException();
        }
        return userAccountRepository.resolveOrCreateClerkUser(issuer, subject);
    }

    private String normalizeEmail(String email) {
        return email.strip().toLowerCase(Locale.ROOT);
    }
}
