package org.aiincubator.ilmai.auth.service;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken.Payload;
import lombok.RequiredArgsConstructor;
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
import org.aiincubator.ilmai.common.i18n.SupportedLocale;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository users;
    private final UserIdentityRepository identities;
    private final GoogleIdentityService googleIdentity;
    private final JwtService jwt;
    private final JwtDecoder jwtDecoder;
    private final RefreshTokenStore refreshTokens;
    private final AuthMapper authMapper;
    private final ApplicationEventPublisher events;

    @Transactional
    public TokenPairResponse loginWithGoogle(String googleIdToken) {
        GoogleIdentity identity = googleIdentity.verify(googleIdToken)
                .orElseThrow(() -> new AuthException(AuthException.Reason.INVALID_GOOGLE_TOKEN));
        if (!identity.emailVerified()) {
            throw new AuthException(AuthException.Reason.EMAIL_NOT_VERIFIED);
        }
        User user = findOrLinkOrCreate(identity);
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new AuthException(AuthException.Reason.USER_DISABLED);
        }
        return issueTokens(user.getId(), UUID.randomUUID());
    }

    @Transactional
    public TokenPairResponse loginAsDev(String email, String name) {
        User user = users.findByUsername(email).orElseGet(() -> {
            User created = new User();
            created.setUsername(email);
            created.setStatus(UserStatus.ACTIVE);
            User persisted = users.save(created);
            events.publishEvent(new UserRegisteredEvent(
                    persisted.getId(),
                    SupportedLocale.DEFAULT,
                    name));
            return persisted;
        });
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new AuthException(AuthException.Reason.USER_DISABLED);
        }
        return issueTokens(user.getId(), UUID.randomUUID());
    }

    @Transactional
    public TokenPairResponse refresh(String refreshTokenString) {
        Jwt decoded = decodeRefresh(refreshTokenString);
        UUID userId = parseUserId(decoded.getSubject());
        UUID familyId = parseFamilyId(decoded.getClaimAsString("fam"));
        String jti = decoded.getId();

        RefreshTokenStore.ConsumeResult result = refreshTokens.consume(jti, familyId, decoded.getExpiresAt());
        switch (result) {
            case REUSED -> {
                refreshTokens.revokeFamily(familyId);
                throw new AuthException(AuthException.Reason.REFRESH_TOKEN_REUSED);
            }
            case UNKNOWN -> throw new AuthException(AuthException.Reason.INVALID_REFRESH_TOKEN);
            case SUCCESS -> { /* proceed */ }
        }

        User user = users.findById(userId)
                .orElseThrow(() -> new AuthException(AuthException.Reason.USER_NOT_FOUND));

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new AuthException(AuthException.Reason.USER_DISABLED);
        }

        return issueTokens(user.getId(), familyId);
    }

    public void logout(String refreshTokenString) {
        try {
            Jwt decoded = decodeRefresh(refreshTokenString);
            refreshTokens.revokeActive(decoded.getId());
        } catch (AuthException _) {
        }
    }

    @Transactional(readOnly = true)
    public MeResponse getMe(CurrentUser currentUser) {
        User user = users.findById(currentUser.getUserId())
                .orElseThrow(() -> new AuthException(AuthException.Reason.USER_NOT_FOUND));
        return authMapper.toMeResponse(user);
    }

    private TokenPairResponse issueTokens(UUID userId, UUID familyId) {
        IssuedJwt access = jwt.issueAccessToken(userId, familyId);
        IssuedJwt refresh = jwt.issueRefreshToken(userId, familyId);
        refreshTokens.recordIssued(refresh.jti(), userId, familyId, refresh.expiresAt());
        return new TokenPairResponse(
                access.token(),
                refresh.token(),
                "Bearer",
                access.expiresAt(),
                refresh.expiresAt()
        );
    }

    private User findOrLinkOrCreate(GoogleIdentity identity) {
        Optional<UserIdentity> existing = identities.findByProviderAndProviderUserId(AuthProvider.GOOGLE, identity.subject());
        if (existing.isPresent()) {
            UserIdentity ui = existing.get();
            ui.setLastLoginAt(OffsetDateTime.now());
            ui.setRawProfile(serializePayload(identity.rawPayload()));
            identities.save(ui);
            return users.findById(ui.getUserId())
                    .orElseThrow(() -> new AuthException(AuthException.Reason.USER_NOT_FOUND));
        }

        User user = users.findByUsername(identity.email()).orElseGet(() -> {
            User created = new User();
            created.setUsername(identity.email());
            created.setStatus(UserStatus.ACTIVE);
            User persisted = users.save(created);
            events.publishEvent(new UserRegisteredEvent(
                    persisted.getId(),
                    resolveLocale(identity),
                    extractFirstNameHint(identity)));
            return persisted;
        });

        UserIdentity newLink = new UserIdentity();
        newLink.setUserId(user.getId());
        newLink.setProvider(AuthProvider.GOOGLE);
        newLink.setProviderUserId(identity.subject());
        newLink.setProviderUsername(identity.email());
        newLink.setRawProfile(serializePayload(identity.rawPayload()));
        newLink.setLastLoginAt(OffsetDateTime.now());
        identities.save(newLink);
        return user;
    }

    private Jwt decodeRefresh(String token) {
        Jwt decoded;
        try {
            decoded = jwtDecoder.decode(token);
        } catch (JwtException e) {
            throw new AuthException(AuthException.Reason.INVALID_REFRESH_TOKEN);
        }
        Object type = decoded.getClaims().get("type");
        if (!JwtService.TOKEN_TYPE_REFRESH.equals(type)) {
            throw new AuthException(AuthException.Reason.INVALID_REFRESH_TOKEN);
        }
        return decoded;
    }

    private UUID parseUserId(String subject) {
        try {
            return UUID.fromString(subject);
        } catch (IllegalArgumentException e) {
            throw new AuthException(AuthException.Reason.INVALID_REFRESH_TOKEN);
        }
    }

    private UUID parseFamilyId(String fam) {
        if (fam == null) {
            throw new AuthException(AuthException.Reason.INVALID_REFRESH_TOKEN);
        }
        try {
            return UUID.fromString(fam);
        } catch (IllegalArgumentException e) {
            throw new AuthException(AuthException.Reason.INVALID_REFRESH_TOKEN);
        }
    }

    private Map<String, Object> serializePayload(Payload payload) {
        return payload == null ? Map.of() : new HashMap<>(payload);
    }

    private SupportedLocale resolveLocale(GoogleIdentity identity) {
        Payload payload = identity.rawPayload();
        if (payload == null) {
            return SupportedLocale.DEFAULT;
        }
        Object locale = payload.get("locale");
        if (locale instanceof String s && !s.isBlank()) {
            return SupportedLocale.fromLanguageTag(s).orElse(SupportedLocale.DEFAULT);
        }
        return SupportedLocale.DEFAULT;
    }

    private String extractFirstNameHint(GoogleIdentity identity) {
        Payload payload = identity.rawPayload();
        if (payload == null) {
            return null;
        }
        Object given = payload.get("given_name");
        if (given instanceof String s && !s.isBlank()) {
            return s;
        }
        Object full = payload.get("name");
        if (full instanceof String s && !s.isBlank()) {
            return s;
        }
        return null;
    }
}
