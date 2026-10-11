package com.cloudpulse.security;

import com.cloudpulse.web.Dto.LoginResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

    private final JwtEncoder encoder;
    private final Clock clock;
    private final long ttlMinutes;

    public JwtService(JwtEncoder encoder, Clock clock,
                      @Value("${cloudpulse.jwt.ttl-minutes:480}") long ttlMinutes) {
        this.encoder = encoder;
        this.clock = clock;
        this.ttlMinutes = ttlMinutes;
    }

    public LoginResponse issue(String username) {
        Instant now = clock.instant();
        Instant expires = now.plus(Duration.ofMinutes(ttlMinutes));
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("cloudpulse")
                .subject(username)
                .issuedAt(now)
                .expiresAt(expires)
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String token = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new LoginResponse(token, expires, username);
    }
}
