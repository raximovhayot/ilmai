package org.aiincubator.ilmai.telegram.config;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ConfigurationProperties(prefix = "telegram")
public class TelegramProperties {

    private String botToken;
    private String botUsername;
    private String webhookSecret;
    private String publicBaseUrl;
    private long streamThrottleMs = 1000L;
}
