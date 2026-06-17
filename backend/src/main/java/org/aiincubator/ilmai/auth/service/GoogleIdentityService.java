package org.aiincubator.ilmai.auth.service;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken.Payload;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import org.aiincubator.ilmai.auth.config.AuthProperties;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Collections;
import java.util.Optional;

@Service
public class GoogleIdentityService {

    private final GoogleIdTokenVerifier verifier;
    private final boolean enabled;

    public GoogleIdentityService(AuthProperties props) {
        String clientId = props.getGoogle().getClientId();
        this.enabled = clientId != null && !clientId.isBlank();
        this.verifier = enabled
                ? new GoogleIdTokenVerifier.Builder(new NetHttpTransport(), GsonFactory.getDefaultInstance())
                        .setAudience(Collections.singletonList(clientId))
                        .build()
                : null;
    }

    public Optional<GoogleIdentity> verify(String idTokenString) {
        if (!enabled) {
            throw new IllegalStateException("Google login is not configured: ilmai.auth.google.client-id is empty");
        }
        try {
            GoogleIdToken token = verifier.verify(idTokenString);
            if (token == null) return Optional.empty();
            Payload payload = token.getPayload();
            String subject = payload.getSubject();
            String email = payload.getEmail();
            Boolean emailVerified = payload.getEmailVerified();
            if (subject == null || email == null) return Optional.empty();
            return Optional.of(new GoogleIdentity(
                    subject,
                    email.toLowerCase(),
                    Boolean.TRUE.equals(emailVerified),
                    payload
            ));
        } catch (GeneralSecurityException | IOException _) {
            return Optional.empty();
        }
    }
}
