package org.aiincubator.ilmai.auth.config;

import lombok.RequiredArgsConstructor;
import org.aiincubator.ilmai.common.CurrentUser;
import org.aiincubator.ilmai.auth.security.CurrentUserAuthentication;
import org.aiincubator.ilmai.auth.service.JwtService;
import org.aiincubator.ilmai.auth.service.RefreshTokenStore;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class AccessTokenAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private final RefreshTokenStore refreshTokens;

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        if (!JwtService.TOKEN_TYPE_ACCESS.equals(jwt.getClaimAsString("type"))) {
            throw new InvalidBearerTokenException("Bearer token is not an access token");
        }
        String fam = jwt.getClaimAsString("fam");
        if (fam == null) {
            throw new InvalidBearerTokenException("Access token missing family claim");
        }
        UUID familyId;
        try {
            familyId = UUID.fromString(fam);
        } catch (IllegalArgumentException _) {
            throw new InvalidBearerTokenException("Access token has invalid family claim");
        }
        if (refreshTokens.isFamilyRevoked(familyId)) {
            throw new InvalidBearerTokenException("Session revoked");
        }
        String sub = jwt.getSubject();
        if (sub == null) {
            throw new InvalidBearerTokenException("Access token missing subject");
        }
        UUID userId;
        try {
            userId = UUID.fromString(sub);
        } catch (IllegalArgumentException _) {
            throw new InvalidBearerTokenException("Access token has invalid subject");
        }
        return new CurrentUserAuthentication(new CurrentUser(userId), jwt, List.of());
    }
}
