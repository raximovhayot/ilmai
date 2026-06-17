package org.aiincubator.ilmai.auth.service;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken.Payload;

public record GoogleIdentity(String subject,
                             String email,
                             boolean emailVerified,
                             Payload rawPayload) {
}
