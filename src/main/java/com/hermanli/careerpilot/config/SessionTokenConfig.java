package com.hermanli.careerpilot.config;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.OctetSequenceKey;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;

@Configuration
public class SessionTokenConfig {

    public static final String ISSUER = "careerpilot";

    @Bean
    public SecretKey sessionSigningKey(
            @Value("${careerpilot.auth.jwt-secret:}") String configuredSecret,
            @Value("${careerpilot.auth.cookie-secure:false}") boolean secureCookie
    ) throws NoSuchAlgorithmException {
        if (configuredSecret == null || configuredSecret.isBlank()) {
            if (secureCookie) {
                throw new IllegalStateException(
                        "CAREERPILOT_JWT_SECRET is required when secure authentication cookies are enabled."
                );
            }
            KeyGenerator keyGenerator = KeyGenerator.getInstance("HmacSHA256");
            keyGenerator.init(256);
            return keyGenerator.generateKey();
        }

        byte[] secretBytes = configuredSecret.getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < 32) {
            throw new IllegalStateException("CAREERPILOT_JWT_SECRET must contain at least 32 UTF-8 bytes.");
        }
        return new SecretKeySpec(secretBytes, "HmacSHA256");
    }

    @Bean
    public JwtEncoder jwtEncoder(SecretKey sessionSigningKey) {
        OctetSequenceKey jwk = new OctetSequenceKey.Builder(sessionSigningKey)
                .algorithm(JWSAlgorithm.HS256)
                .build();
        JWKSource<SecurityContext> jwkSource =
                (selector, context) -> selector.select(new JWKSet(jwk));
        return new NimbusJwtEncoder(jwkSource);
    }

    @Bean
    public JwtDecoder jwtDecoder(SecretKey sessionSigningKey) {
        NimbusJwtDecoder jwtDecoder = NimbusJwtDecoder.withSecretKey(sessionSigningKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        jwtDecoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                new JwtTimestampValidator(Duration.ZERO),
                new JwtIssuerValidator(ISSUER)
        ));
        return jwtDecoder;
    }

    @Bean
    public Clock sessionClock() {
        return Clock.systemUTC();
    }
}
