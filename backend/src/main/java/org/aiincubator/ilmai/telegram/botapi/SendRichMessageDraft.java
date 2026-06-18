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
import org.telegram.telegrambots.meta.api.methods.botapimethods.BotApiMethodBoolean;
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
public class SendRichMessageDraft extends BotApiMethodBoolean {
    public static final String PATH = "sendRichMessageDraft";

    private static final String CHAT_ID_FIELD = "chat_id";
    private static final String MESSAGE_THREAD_ID_FIELD = "message_thread_id";
    private static final String DRAFT_ID_FIELD = "draft_id";
    private static final String RICH_MESSAGE_FIELD = "rich_message";

    @JsonProperty(CHAT_ID_FIELD)
    @NonNull
    private Long chatId;

    @JsonProperty(MESSAGE_THREAD_ID_FIELD)
    private Integer messageThreadId;

    @JsonProperty(DRAFT_ID_FIELD)
    @NonNull
    private Integer draftId;

    @JsonProperty(RICH_MESSAGE_FIELD)
    @NonNull
    private InputRichMessage richMessage;

    @Override
    public String getMethod() {
        return PATH;
    }

    @Override
    public void validate() throws TelegramApiValidationException {
        if (chatId == null || chatId == 0L) {
            throw new TelegramApiValidationException("ChatId can't be empty", this);
        }
        if (draftId == null || draftId == 0) {
            throw new TelegramApiValidationException("DraftId can't be empty and must be non-zero", this);
        }
        if (richMessage == null) {
            throw new TelegramApiValidationException("RichMessage can't be empty", this);
        }
        richMessage.validate();
    }
}
