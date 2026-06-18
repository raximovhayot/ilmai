package org.aiincubator.ilmai.telegram.botapi;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.telegram.telegrambots.meta.exceptions.TelegramApiValidationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;

class SendRichMessageDraftTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void method_isSendRichMessageDraft() {
        SendRichMessageDraft request = SendRichMessageDraft.builder()
                .chatId(42L)
                .draftId(7)
                .richMessage(InputRichMessage.builder().markdown("# Hi").build())
                .build();

        assertThat(request.getMethod()).isEqualTo("sendRichMessageDraft");
    }

    @Test
    void serialize_usesBotApiFieldNames() throws Exception {
        SendRichMessageDraft request = SendRichMessageDraft.builder()
                .chatId(42L)
                .draftId(7)
                .richMessage(InputRichMessage.builder().markdown("# Heading\n**bold**").build())
                .build();

        String json = mapper.writeValueAsString(request);

        assertThat(json).contains("\"chat_id\":42");
        assertThat(json).contains("\"draft_id\":7");
        assertThat(json).contains("\"rich_message\"");
        assertThat(json).contains("\"markdown\"");
        assertThat(json).doesNotContain("\"html\"");
        assertThat(json).doesNotContain("message_thread_id");
    }

    @Test
    void validate_passesForMarkdownOnly() {
        SendRichMessageDraft request = SendRichMessageDraft.builder()
                .chatId(42L)
                .draftId(7)
                .richMessage(InputRichMessage.builder().markdown("# Hi").build())
                .build();

        assertThatCode(request::validate).doesNotThrowAnyException();
    }

    @Test
    void validate_rejectsZeroDraftId() {
        SendRichMessageDraft request = SendRichMessageDraft.builder()
                .chatId(42L)
                .draftId(0)
                .richMessage(InputRichMessage.builder().markdown("# Hi").build())
                .build();

        assertThatThrownBy(request::validate).isInstanceOf(TelegramApiValidationException.class);
    }

    @Test
    void validate_rejectsBothHtmlAndMarkdown() {
        SendRichMessageDraft request = SendRichMessageDraft.builder()
                .chatId(42L)
                .draftId(7)
                .richMessage(InputRichMessage.builder().markdown("# Hi").html("<b>Hi</b>").build())
                .build();

        assertThatThrownBy(request::validate).isInstanceOf(TelegramApiValidationException.class);
    }

    @Test
    void validate_rejectsNeitherHtmlNorMarkdown() {
        SendRichMessageDraft request = SendRichMessageDraft.builder()
                .chatId(42L)
                .draftId(7)
                .richMessage(InputRichMessage.builder().build())
                .build();

        assertThatThrownBy(request::validate).isInstanceOf(TelegramApiValidationException.class);
    }
}
