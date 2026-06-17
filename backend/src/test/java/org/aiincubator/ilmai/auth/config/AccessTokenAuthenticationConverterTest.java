package org.aiincubator.ilmai.auth.config;

import org.aiincubator.ilmai.common.CurrentUser;
import org.aiincubator.ilmai.auth.security.CurrentUserAuthentication;
import org.aiincubator.ilmai.auth.service.JwtService;
import org.aiincubator.ilmai.auth.service.RefreshTokenStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AccessTokenAuthenticationConverterTest {

    @Mock RefreshTokenStore refreshTokens;

    private AccessTokenAuthenticationConverter converter;

    @BeforeEach
    void setUp() {
        converter = new AccessTokenAuthenticationConverter(refreshTokens);
    }

    @Test
    void convert_returnsCurrentUserAuthenticationOnValidAccessToken() {
        UUID userId = UUID.randomUUID();
        UUID familyId = UUID.randomUUID();
        Jwt jwt = accessTokenBuilder(userId, familyId).build();
        given(refreshTokens.isFamilyRevoked(familyId)).willReturn(false);

        AbstractAuthenticationToken authentication = converter.convert(jwt);

        assertThat(authentication).isInstanceOf(CurrentUserAuthentication.class);
        CurrentUserAuthentication cua = (CurrentUserAuthentication) authentication;
        assertThat(cua.getPrincipal()).isEqualTo(new CurrentUser(userId));
        assertThat(((CurrentUser) cua.getPrincipal()).getUserId()).isEqualTo(userId);
        assertThat(cua.getToken()).isSameAs(jwt);
        assertThat(cua.isAuthenticated()).isTrue();
        assertThat(cua.getName()).isEqualTo(userId.toString());
    }

    @Test
    void convert_rejectsRefreshTokenType() {
        Jwt jwt = accessTokenBuilder(UUID.randomUUID(), UUID.randomUUID())
                .claim("type", JwtService.TOKEN_TYPE_REFRESH)
                .build();

        assertThatThrownBy(() -> converter.convert(jwt))
                .isInstanceOf(InvalidBearerTokenException.class)
                .hasMessageContaining("not an access token");
        verify(refreshTokens, never()).isFamilyRevoked(any(UUID.class));
    }

    @Test
    void convert_rejectsMissingFamilyClaim() {
        Jwt jwt = Jwt.withTokenValue("dummy")
                .header("alg", "HS256")
                .subject(UUID.randomUUID().toString())
                .issuedAt(Instant.now().minusSeconds(60))
                .expiresAt(Instant.now().plusSeconds(60))
                .claim("type", JwtService.TOKEN_TYPE_ACCESS)
                .build();

        assertThatThrownBy(() -> converter.convert(jwt))
                .isInstanceOf(InvalidBearerTokenException.class)
                .hasMessageContaining("missing family claim");
    }

    @Test
    void convert_rejectsMalformedFamilyClaim() {
        Jwt jwt = accessTokenBuilder(UUID.randomUUID(), UUID.randomUUID())
                .claim("fam", "not-a-uuid")
                .build();

        assertThatThrownBy(() -> converter.convert(jwt))
                .isInstanceOf(InvalidBearerTokenException.class)
                .hasMessageContaining("invalid family claim");
    }

    @Test
    void convert_rejectsRevokedFamily() {
        UUID familyId = UUID.randomUUID();
        Jwt jwt = accessTokenBuilder(UUID.randomUUID(), familyId).build();
        given(refreshTokens.isFamilyRevoked(familyId)).willReturn(true);

        assertThatThrownBy(() -> converter.convert(jwt))
                .isInstanceOf(InvalidBearerTokenException.class)
                .hasMessageContaining("Session revoked");
    }

    @Test
    void convert_rejectsMissingSubject() {
        UUID familyId = UUID.randomUUID();
        Jwt jwt = Jwt.withTokenValue("dummy")
                .header("alg", "HS256")
                .issuedAt(Instant.now().minusSeconds(60))
                .expiresAt(Instant.now().plusSeconds(60))
                .claim("type", JwtService.TOKEN_TYPE_ACCESS)
                .claim("fam", familyId.toString())
                .build();
        given(refreshTokens.isFamilyRevoked(familyId)).willReturn(false);

        assertThatThrownBy(() -> converter.convert(jwt))
                .isInstanceOf(InvalidBearerTokenException.class)
                .hasMessageContaining("missing subject");
    }

    @Test
    void convert_rejectsMalformedSubject() {
        UUID familyId = UUID.randomUUID();
        Jwt jwt = Jwt.withTokenValue("dummy")
                .header("alg", "HS256")
                .subject("not-a-uuid")
                .issuedAt(Instant.now().minusSeconds(60))
                .expiresAt(Instant.now().plusSeconds(60))
                .claim("type", JwtService.TOKEN_TYPE_ACCESS)
                .claim("fam", familyId.toString())
                .build();
        given(refreshTokens.isFamilyRevoked(familyId)).willReturn(false);

        assertThatThrownBy(() -> converter.convert(jwt))
                .isInstanceOf(InvalidBearerTokenException.class)
                .hasMessageContaining("invalid subject");
    }

    private static Jwt.Builder accessTokenBuilder(UUID userId, UUID familyId) {
        return Jwt.withTokenValue("dummy")
                .header("alg", "HS256")
                .subject(userId.toString())
                .issuedAt(Instant.now().minusSeconds(60))
                .expiresAt(Instant.now().plusSeconds(60))
                .claim("type", JwtService.TOKEN_TYPE_ACCESS)
                .claim("fam", familyId.toString());
    }
}
