package com.hermanli.careerpilot.publicrag;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Set;

final class PublicRagJwtValidator implements OAuth2TokenValidator<Jwt> {

    private static final OAuth2Error INVALID_TOKEN = new OAuth2Error(
            "invalid_token",
            "The public RAG session token is invalid.",
            null
    );

    private final Set<String> authorizedParties;

    PublicRagJwtValidator(Set<String> authorizedParties) {
        this.authorizedParties = Set.copyOf(authorizedParties);
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt jwt) {
        String subject = jwt.getSubject();
        String authorizedParty = jwt.getClaimAsString("azp");
        String sessionStatus = jwt.getClaimAsString("sts");
        if (subject == null || subject.isBlank()
                || (authorizedParty != null && !authorizedParties.contains(authorizedParty))
                || "pending".equals(sessionStatus)) {
            return OAuth2TokenValidatorResult.failure(INVALID_TOKEN);
        }
        return OAuth2TokenValidatorResult.success();
    }
}
