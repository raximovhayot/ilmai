package org.aiincubator.ilmai.auth.payload;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TokenPairResponse {

    private String accessToken;
    private String refreshToken;
    private String tokenType;
    private Instant accessExpiresAt;
    private Instant refreshExpiresAt;
}
