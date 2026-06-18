package org.aiincubator.ilmai.telegram.botapi;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import org.telegram.telegrambots.meta.api.methods.botapimethods.BotApiMethodMessage;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboard;
import org.telegram.telegrambots.meta.exceptions.TelegramApiValidationException;

@SuppressWarnings("unused")
@EqualsAndHashCode(callSuper = false)
@Getter
@Setter
@ToString
@RequiredArgsConstructor
@AllArgsConstructor
@SuperBuilder
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SendRichMessage extends BotApiMethodMessage {
    public static final String PATH = "sendRichMessage";

    private static final String CHAT_ID_FIELD = "chat_id";
    private static final String MESSAGE_THREAD_ID_FIELD = "message_thread_id";
    private static final String RICH_MESSAGE_FIELD = "rich_message";
    private static final String DISABLE_NOTIFICATION_FIELD = "disable_notification";
    private static final String REPLY_MARKUP_FIELD = "reply_markup";

    @JsonProperty(CHAT_ID_FIELD)
    @NonNull
    private Long chatId;

    @JsonProperty(MESSAGE_THREAD_ID_FIELD)
    private Integer messageThreadId;

    @JsonProperty(RICH_MESSAGE_FIELD)
    @NonNull
    private InputRichMessage richMessage;

    @JsonProperty(DISABLE_NOTIFICATION_FIELD)
    private Boolean disableNotification;

    @JsonProperty(REPLY_MARKUP_FIELD)
    private ReplyKeyboard replyMarkup;

    @Override
    public String getMethod() {
        return PATH;
    }

    @Override
    public void validate() throws TelegramApiValidationException {
        if (chatId == null || chatId == 0L) {
            throw new TelegramApiValidationException("ChatId can't be empty", this);
        }
        if (richMessage == null) {
            throw new TelegramApiValidationException("RichMessage can't be empty", this);
        }
        richMessage.validate();
        if (replyMarkup != null) {
            replyMarkup.validate();
        }
    }
}
