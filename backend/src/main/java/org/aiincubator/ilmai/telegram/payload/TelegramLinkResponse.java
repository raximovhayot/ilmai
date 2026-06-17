package org.aiincubator.ilmai.telegram.payload;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TelegramLinkResponse {

    private UUID id;
    private String telegramUsername;
    private Long chatId;
    private OffsetDateTime linkedAt;
    private String linkCode;
    private OffsetDateTime linkCodeExpiresAt;
    private String botUsername;
}
