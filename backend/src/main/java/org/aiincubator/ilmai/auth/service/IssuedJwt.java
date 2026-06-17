package org.aiincubator.ilmai.auth.service;

import java.time.Instant;
import java.util.Map;

public record IssuedJwt(String token,
                        String jti,
                        Instant expiresAt) {

    public Map<String, Object> toResponseClaims() {
        return Map.of("jti", jti, "exp", expiresAt.toString());
    }
}
