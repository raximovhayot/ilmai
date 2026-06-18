package org.aiincubator.ilmai.telegram.botapi;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.telegram.telegrambots.meta.exceptions.TelegramApiValidationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SendRichMessageTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void method_isSendRichMessage() {
        SendRichMessage request = SendRichMessage.builder()
                .chatId(42L)
                .richMessage(InputRichMessage.builder().markdown("# Hi").build())
                .build();

        assertThat(request.getMethod()).isEqualTo("sendRichMessage");
    }

    @Test
    void serialize_usesBotApiFieldNames() throws Exception {
        SendRichMessage request = SendRichMessage.builder()
                .chatId(42L)
                .richMessage(InputRichMessage.builder().markdown("# Heading\n**bold**").build())
                .build();

        String json = mapper.writeValueAsString(request);

        assertThat(json).contains("\"chat_id\":42");
        assertThat(json).contains("\"rich_message\"");
        assertThat(json).contains("\"markdown\"");
        assertThat(json).doesNotContain("\"draft_id\"");
        assertThat(json).doesNotContain("\"html\"");
        assertThat(json).doesNotContain("message_thread_id");
        assertThat(json).doesNotContain("reply_markup");
    }

    @Test
    void validate_passesForMarkdownOnly() {
        SendRichMessage request = SendRichMessage.builder()
                .chatId(42L)
                .richMessage(InputRichMessage.builder().markdown("# Hi").build())
                .build();

        assertThatCode(request::validate).doesNotThrowAnyException();
    }

    @Test
    void validate_rejectsZeroChatId() {
        SendRichMessage request = SendRichMessage.builder()
                .chatId(0L)
                .richMessage(InputRichMessage.builder().markdown("# Hi").build())
                .build();

        assertThatThrownBy(request::validate).isInstanceOf(TelegramApiValidationException.class);
    }

    @Test
    void validate_rejectsBothHtmlAndMarkdown() {
        SendRichMessage request = SendRichMessage.builder()
                .chatId(42L)
                .richMessage(InputRichMessage.builder().markdown("# Hi").html("<b>Hi</b>").build())
                .build();

        assertThatThrownBy(request::validate).isInstanceOf(TelegramApiValidationException.class);
    }
}
