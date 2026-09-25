package com.hermanli.careerpilot.identity;

import com.hermanli.careerpilot.config.SessionTokenConfig;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.OptionalLong;

@Service
public class SessionTokenService {

    private final JwtEncoder jwtEncoder;
    private final JwtDecoder jwtDecoder;
    private final Clock clock;
    private final Duration sessionDuration;

    public SessionTokenService(
            JwtEncoder jwtEncoder,
            @Qualifier("jwtDecoder") JwtDecoder jwtDecoder,
            Clock clock,
            @Value("${careerpilot.auth.session-duration}") Duration sessionDuration
    ) {
        if (sessionDuration.isZero() || sessionDuration.isNegative()) {
            throw new IllegalArgumentException("Session duration must be positive.");
        }
        this.jwtEncoder = jwtEncoder;
        this.jwtDecoder = jwtDecoder;
        this.clock = clock;
        this.sessionDuration = sessionDuration;
    }

    public String issue(long userId) {
        Instant issuedAt = clock.instant();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(SessionTokenConfig.ISSUER)
                .subject(Long.toString(userId))
                .issuedAt(issuedAt)
                .expiresAt(issuedAt.plus(sessionDuration))
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    public OptionalLong decodeUserId(String token) {
        try {
            Jwt jwt = jwtDecoder.decode(token);
            long userId = Long.parseLong(jwt.getSubject());
            return userId > 0 ? OptionalLong.of(userId) : OptionalLong.empty();
        } catch (JwtException | NumberFormatException exception) {
            return OptionalLong.empty();
        }
    }
}
