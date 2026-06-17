package org.aiincubator.ilmai.auth.service;

import lombok.RequiredArgsConstructor;
import org.aiincubator.ilmai.auth.config.AuthProperties;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class JwtService {

    public static final String TOKEN_TYPE_ACCESS = "access";
    public static final String TOKEN_TYPE_REFRESH = "refresh";

    private final JwtEncoder encoder;
    private final AuthProperties props;

    public IssuedJwt issueAccessToken(UUID userId, UUID familyId) {
        Instant now = Instant.now();
        Instant exp = now.plus(props.getJwt().getAccessTtl());
        String jti = UUID.randomUUID().toString();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(props.getJwt().getIssuer())
                .subject(userId.toString())
                .issuedAt(now)
                .expiresAt(exp)
                .id(jti)
                .claim("type", TOKEN_TYPE_ACCESS)
                .claim("fam", familyId.toString())
                .build();
        Jwt jwt = encoder.encode(JwtEncoderParameters.from(macHeader(), claims));
        return new IssuedJwt(jwt.getTokenValue(), jti, exp);
    }

    public IssuedJwt issueRefreshToken(UUID userId, UUID familyId) {
        Instant now = Instant.now();
        Instant exp = now.plus(props.getRefreshToken().getTtl());
        String jti = UUID.randomUUID().toString();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(props.getJwt().getIssuer())
                .subject(userId.toString())
                .issuedAt(now)
                .expiresAt(exp)
                .id(jti)
                .claim("type", TOKEN_TYPE_REFRESH)
                .claim("fam", familyId.toString())
                .build();
        Jwt jwt = encoder.encode(JwtEncoderParameters.from(macHeader(), claims));
        return new IssuedJwt(jwt.getTokenValue(), jti, exp);
    }

    private JwsHeader macHeader() {
        return JwsHeader.with(MacAlgorithm.HS256).build();
    }
}
