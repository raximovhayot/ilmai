package org.aiincubator.ilmai.telegram.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.meta.generics.TelegramClient;

@Configuration
@EnableConfigurationProperties(TelegramProperties.class)
@EnableScheduling
public class TelegramConfig {

    @Bean
    TelegramClient telegramClient(TelegramProperties properties) {
        String botToken = properties.getBotToken();
        return new OkHttpTelegramClient(botToken == null ? "" : botToken);
    }
}
