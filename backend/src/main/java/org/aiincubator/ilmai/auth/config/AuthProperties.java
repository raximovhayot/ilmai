package org.aiincubator.ilmai.auth.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "auth")
public class AuthProperties {

    @NotNull
    private Jwt jwt = new Jwt();

    @NotNull
    private Google google = new Google();

    @NotNull
    private RefreshToken refreshToken = new RefreshToken();

    @NotNull
    private DevLogin devLogin = new DevLogin();

    @Getter
    @Setter
    public static class Jwt {
        @NotBlank
        private String issuer = "ilmai";

        @NotBlank
        private String secret;

        @NotNull
        @Positive
        private Duration accessTtl = Duration.ofMinutes(15);
    }

    @Getter
    @Setter
    public static class Google {
        private String clientId = "";
    }

    @Getter
    @Setter
    public static class RefreshToken {
        @NotNull
        @Positive
        private Duration ttl = Duration.ofDays(30);
    }

    @Getter
    @Setter
    public static class DevLogin {
        private boolean enabled = false;

        @NotBlank
        private String email = "dev@ilmai.dev";

        @NotBlank
        private String name = "Dev User";
    }
}
