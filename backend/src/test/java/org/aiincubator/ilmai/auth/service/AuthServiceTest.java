package org.aiincubator.ilmai.auth.service;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import org.aiincubator.ilmai.auth.UserRegisteredEvent;
import org.aiincubator.ilmai.auth.domain.AuthProvider;
import org.aiincubator.ilmai.auth.domain.User;
import org.aiincubator.ilmai.auth.domain.UserIdentity;
import org.aiincubator.ilmai.auth.domain.UserIdentityRepository;
import org.aiincubator.ilmai.auth.domain.UserRepository;
import org.aiincubator.ilmai.auth.UserStatus;
import org.aiincubator.ilmai.auth.payload.MeResponse;
import org.aiincubator.ilmai.auth.payload.TokenPairResponse;
import org.aiincubator.ilmai.common.CurrentUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock UserRepository users;
    @Mock UserIdentityRepository identities;
    @Mock GoogleIdentityService googleIdentity;
    @Mock JwtService jwt;
    @Mock JwtDecoder jwtDecoder;
    @Mock RefreshTokenStore refreshTokens;
    @Mock ApplicationEventPublisher events;
    @Spy AuthMapper authMapper = Mappers.getMapper(AuthMapper.class);

    @InjectMocks AuthService authService;

    private GoogleIdToken.Payload payload;

    @BeforeEach
    void setUp() {
        payload = new GoogleIdToken.Payload();
        payload.setSubject("google-sub-123");
        payload.setEmail("a@example.com");
        payload.setEmailVerified(true);
        payload.set("given_name", "Aziza");
        payload.set("name", "Aziza Karimova");
    }

    @Test
    void loginWithGoogle_createsUserAndIdentityOnFirstLogin() {
        var identity = new GoogleIdentity("google-sub-123", "a@example.com", true, payload);
        when(googleIdentity.verify("tok")).thenReturn(Optional.of(identity));
        when(identities.findByProviderAndProviderUserId(AuthProvider.GOOGLE, "google-sub-123"))
                .thenReturn(Optional.empty());
        when(users.findByUsername("a@example.com")).thenReturn(Optional.empty());
        UUID newId = UUID.randomUUID();
        when(users.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(newId);
            return u;
        });
        mockTokenIssuance(newId);

        TokenPairResponse pair = authService.loginWithGoogle("tok");

        verify(users).save(any(User.class));
        verify(identities).save(any(UserIdentity.class));
        verify(events).publishEvent(any(UserRegisteredEvent.class));
        verify(refreshTokens).recordIssued(eq("refresh-jti"), eq(newId), any(UUID.class), any(Instant.class));
        assertThat(pair.getAccessToken()).isEqualTo("access-token");
        assertThat(pair.getRefreshToken()).isEqualTo("refresh-token");
    }

    @Test
    void loginWithGoogle_reusesExistingIdentity() {
        var identity = new GoogleIdentity("google-sub-123", "a@example.com", true, payload);
        UUID existingUserId = UUID.randomUUID();
        UserIdentity existingIdentity = new UserIdentity();
        existingIdentity.setId(UUID.randomUUID());
        existingIdentity.setUserId(existingUserId);
        existingIdentity.setProvider(AuthProvider.GOOGLE);
        existingIdentity.setProviderUserId("google-sub-123");
        User existingUser = new User();
        existingUser.setId(existingUserId);
        existingUser.setUsername("a@example.com");
        existingUser.setStatus(UserStatus.ACTIVE);

        when(googleIdentity.verify("tok")).thenReturn(Optional.of(identity));
        when(identities.findByProviderAndProviderUserId(AuthProvider.GOOGLE, "google-sub-123"))
                .thenReturn(Optional.of(existingIdentity));
        when(users.findById(existingUserId)).thenReturn(Optional.of(existingUser));
        mockTokenIssuance(existingUserId);

        TokenPairResponse pair = authService.loginWithGoogle("tok");

        verify(users, never()).save(any(User.class));
        verify(identities, times(1)).save(any(UserIdentity.class));
        verify(events, never()).publishEvent(any(UserRegisteredEvent.class));
        assertThat(pair.getRefreshToken()).isEqualTo("refresh-token");
    }

    @Test
    void loginWithGoogle_linksToExistingUserByUsername() {
        var identity = new GoogleIdentity("google-sub-123", "a@example.com", true, payload);
        UUID existingUserId = UUID.randomUUID();
        User existingUser = new User();
        existingUser.setId(existingUserId);
        existingUser.setUsername("a@example.com");
        existingUser.setStatus(UserStatus.ACTIVE);

        when(googleIdentity.verify("tok")).thenReturn(Optional.of(identity));
        when(identities.findByProviderAndProviderUserId(AuthProvider.GOOGLE, "google-sub-123"))
                .thenReturn(Optional.empty());
        when(users.findByUsername("a@example.com")).thenReturn(Optional.of(existingUser));
        mockTokenIssuance(existingUserId);

        authService.loginWithGoogle("tok");

        verify(users, never()).save(any(User.class));
        verify(identities, times(1)).save(any(UserIdentity.class));
        verify(events, never()).publishEvent(any(UserRegisteredEvent.class));
    }

    @Test
    void loginWithGoogle_rejectsInvalidGoogleToken() {
        when(googleIdentity.verify("bad")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.loginWithGoogle("bad"))
                .isInstanceOf(AuthException.class)
                .extracting(e -> ((AuthException) e).getReason())
                .isEqualTo(AuthException.Reason.INVALID_GOOGLE_TOKEN);
    }

    @Test
    void loginWithGoogle_rejectsUnverifiedEmail() {
        var identity = new GoogleIdentity("s", "a@example.com", false, payload);
        when(googleIdentity.verify("tok")).thenReturn(Optional.of(identity));

        assertThatThrownBy(() -> authService.loginWithGoogle("tok"))
                .isInstanceOf(AuthException.class)
                .extracting(e -> ((AuthException) e).getReason())
                .isEqualTo(AuthException.Reason.EMAIL_NOT_VERIFIED);
    }

    @Test
    void loginWithGoogle_rejectsDisabledUser() {
        var identity = new GoogleIdentity("s", "a@example.com", true, payload);
        UUID id = UUID.randomUUID();
        UserIdentity link = new UserIdentity();
        link.setUserId(id);
        link.setProvider(AuthProvider.GOOGLE);
        link.setProviderUserId("s");
        User disabled = new User();
        disabled.setId(id);
        disabled.setUsername("a@example.com");
        disabled.setStatus(UserStatus.DISABLED);
        when(googleIdentity.verify("tok")).thenReturn(Optional.of(identity));
        when(identities.findByProviderAndProviderUserId(AuthProvider.GOOGLE, "s")).thenReturn(Optional.of(link));
        when(users.findById(id)).thenReturn(Optional.of(disabled));

        assertThatThrownBy(() -> authService.loginWithGoogle("tok"))
                .isInstanceOf(AuthException.class)
                .extracting(e -> ((AuthException) e).getReason())
                .isEqualTo(AuthException.Reason.USER_DISABLED);
    }

    @Test
    void refresh_rotatesAndIssuesNewPair() {
        UUID userId = UUID.randomUUID();
        UUID familyId = UUID.randomUUID();
        Jwt refreshJwt = refreshJwtWith(userId, familyId, "old-jti");
        when(jwtDecoder.decode("refresh")).thenReturn(refreshJwt);
        when(refreshTokens.consume(eq("old-jti"), eq(familyId), any(Instant.class)))
                .thenReturn(RefreshTokenStore.ConsumeResult.SUCCESS);
        User refreshUser = new User();
        refreshUser.setId(userId);
        refreshUser.setUsername("a@example.com");
        refreshUser.setStatus(UserStatus.ACTIVE);
        when(users.findById(userId)).thenReturn(Optional.of(refreshUser));
        mockTokenIssuance(userId);

        TokenPairResponse pair = authService.refresh("refresh");

        verify(refreshTokens).consume(eq("old-jti"), eq(familyId), any(Instant.class));
        verify(refreshTokens).recordIssued(eq("refresh-jti"), eq(userId), eq(familyId), any(Instant.class));
        verify(refreshTokens, never()).revokeFamily(any(UUID.class));
        assertThat(pair.getAccessToken()).isEqualTo("access-token");
    }

    @Test
    void refresh_rejectsUnknownToken() {
        UUID userId = UUID.randomUUID();
        UUID familyId = UUID.randomUUID();
        Jwt refreshJwt = refreshJwtWith(userId, familyId, "stale-jti");
        when(jwtDecoder.decode("refresh")).thenReturn(refreshJwt);
        when(refreshTokens.consume(eq("stale-jti"), eq(familyId), any(Instant.class)))
                .thenReturn(RefreshTokenStore.ConsumeResult.UNKNOWN);

        assertThatThrownBy(() -> authService.refresh("refresh"))
                .isInstanceOf(AuthException.class)
                .extracting(e -> ((AuthException) e).getReason())
                .isEqualTo(AuthException.Reason.INVALID_REFRESH_TOKEN);
        verify(refreshTokens, never()).revokeFamily(any(UUID.class));
    }

    @Test
    void refresh_detectsReuseAndRevokesFamily() {
        UUID userId = UUID.randomUUID();
        UUID familyId = UUID.randomUUID();
        Jwt refreshJwt = refreshJwtWith(userId, familyId, "reused-jti");
        when(jwtDecoder.decode("refresh")).thenReturn(refreshJwt);
        when(refreshTokens.consume(eq("reused-jti"), eq(familyId), any(Instant.class)))
                .thenReturn(RefreshTokenStore.ConsumeResult.REUSED);

        assertThatThrownBy(() -> authService.refresh("refresh"))
                .isInstanceOf(AuthException.class)
                .extracting(e -> ((AuthException) e).getReason())
                .isEqualTo(AuthException.Reason.REFRESH_TOKEN_REUSED);
        verify(refreshTokens).revokeFamily(familyId);
        verify(refreshTokens, never()).recordIssued(anyString(), any(UUID.class), any(UUID.class), any(Instant.class));
    }

    @Test
    void refresh_rejectsAccessTokenUsedAsRefresh() {
        UUID userId = UUID.randomUUID();
        Jwt accessJwt = jwtWith(userId, JwtService.TOKEN_TYPE_ACCESS, "jti");
        when(jwtDecoder.decode("access")).thenReturn(accessJwt);

        assertThatThrownBy(() -> authService.refresh("access"))
                .isInstanceOf(AuthException.class)
                .extracting(e -> ((AuthException) e).getReason())
                .isEqualTo(AuthException.Reason.INVALID_REFRESH_TOKEN);
    }

    @Test
    void refresh_rejectsMalformedJwt() {
        when(jwtDecoder.decode(anyString())).thenThrow(new JwtException("bad"));

        assertThatThrownBy(() -> authService.refresh("garbage"))
                .isInstanceOf(AuthException.class)
                .extracting(e -> ((AuthException) e).getReason())
                .isEqualTo(AuthException.Reason.INVALID_REFRESH_TOKEN);
    }

    @Test
    void logout_revokesStoredRefresh() {
        UUID userId = UUID.randomUUID();
        Jwt refreshJwt = refreshJwtWith(userId, UUID.randomUUID(), "jti-to-kill");
        when(jwtDecoder.decode("r")).thenReturn(refreshJwt);

        authService.logout("r");

        verify(refreshTokens).revokeActive("jti-to-kill");
    }

    @Test
    void logout_silentlyIgnoresInvalidToken() {
        when(jwtDecoder.decode(anyString())).thenThrow(new JwtException("bad"));

        authService.logout("garbage");

        verify(refreshTokens, never()).revokeActive(anyString());
    }

    @Test
    void getMe_returnsUserById() {
        UUID id = UUID.randomUUID();
        User user = new User();
        user.setId(id);
        user.setUsername("a@example.com");
        user.setStatus(UserStatus.ACTIVE);
        user.setCreatedAt(OffsetDateTime.now());
        when(users.findById(id)).thenReturn(Optional.of(user));

        MeResponse result = authService.getMe(new CurrentUser(id));

        assertThat(result.getId()).isEqualTo(id);
        assertThat(result.getUsername()).isEqualTo("a@example.com");
    }

    @Test
    void getMe_throwsWhenUserMissing() {
        UUID id = UUID.randomUUID();
        CurrentUser currentUser = new CurrentUser(id);
        when(users.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.getMe(currentUser))
                .isInstanceOf(AuthException.class)
                .extracting(e -> ((AuthException) e).getReason())
                .isEqualTo(AuthException.Reason.USER_NOT_FOUND);
    }

    private void mockTokenIssuance(UUID userId) {
        Instant accessExp = Instant.now().plusSeconds(900);
        Instant refreshExp = Instant.now().plusSeconds(60 * 60 * 24 * 30);
        when(jwt.issueAccessToken(eq(userId), any(UUID.class)))
                .thenReturn(new IssuedJwt("access-token", "access-jti", accessExp));
        when(jwt.issueRefreshToken(eq(userId), any(UUID.class)))
                .thenReturn(new IssuedJwt("refresh-token", "refresh-jti", refreshExp));
    }

    private Jwt jwtWith(UUID userId, String type, String jti) {
        return Jwt.withTokenValue("dummy")
                .header("alg", "HS256")
                .subject(userId.toString())
                .claim("type", type)
                .claim("jti", jti)
                .jti(jti)
                .issuedAt(Instant.now().minusSeconds(60))
                .expiresAt(Instant.now().plusSeconds(60))
                .claims(c -> c.putAll(Map.of()))
                .build();
    }

    private Jwt refreshJwtWith(UUID userId, UUID familyId, String jti) {
        return Jwt.withTokenValue("dummy")
                .header("alg", "HS256")
                .subject(userId.toString())
                .claim("type", JwtService.TOKEN_TYPE_REFRESH)
                .claim("fam", familyId.toString())
                .jti(jti)
                .issuedAt(Instant.now().minusSeconds(60))
                .expiresAt(Instant.now().plusSeconds(60))
                .build();
    }
}
