package com.hermanli.careerpilot.publicrag;

import com.hermanli.careerpilot.identity.UserAccountService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import java.util.Set;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "careerpilot.public-rag.auth.enabled", havingValue = "true")
public class PublicRagAuthConfiguration {

    @Bean("publicRagClerkJwtDecoder")
    JwtDecoder publicRagClerkJwtDecoder(PublicRagAuthProperties properties) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(
                        properties.getIssuer() + "/.well-known/jwks.json"
                )
                .jwsAlgorithm(SignatureAlgorithm.RS256)
                .build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(properties.getIssuer()),
                new PublicRagJwtValidator(Set.copyOf(properties.getAuthorizedParties()))
        ));
        return decoder;
    }

    @Bean("publicRagAuthenticationInterceptor")
    PublicRagAuthenticationInterceptor publicRagAuthenticationInterceptor(
            @Qualifier("publicRagClerkJwtDecoder") JwtDecoder jwtDecoder,
            UserAccountService userAccountService
    ) {
        return new PublicRagAuthenticationInterceptor(jwtDecoder, userAccountService);
    }

    @Bean("publicRagSecurityAuditInterceptor")
    PublicRagSecurityAuditInterceptor publicRagSecurityAuditInterceptor() {
        return new PublicRagSecurityAuditInterceptor();
    }
}
