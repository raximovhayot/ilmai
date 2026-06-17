package org.aiincubator.ilmai.telegram.service;

import org.aiincubator.ilmai.telegram.config.TelegramProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.GetFile;
import org.telegram.telegrambots.meta.api.methods.polls.SendPoll;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendMessageDraft;
import org.telegram.telegrambots.meta.api.objects.File;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.api.objects.polls.Poll;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TelegramApiClientTest {

    @Mock TelegramClient telegramClient;

    private TelegramApiClient client;

    @BeforeEach
    void setUp() {
        client = new TelegramApiClient(telegramClient, new TelegramProperties("token", "bot", "secret", "https://example.com", 1000L));
    }

    @Test
    void sendMessage_buildsHtmlRequest() throws TelegramApiException {
        ArgumentCaptor<SendMessage> captor = ArgumentCaptor.forClass(SendMessage.class);

        assertThat(client.sendMessage(42L, "hello")).isTrue();

        verify(telegramClient).execute(captor.capture());
        SendMessage sent = captor.getValue();
        assertThat(sent.getChatId()).isEqualTo("42");
        assertThat(sent.getText()).isEqualTo("hello");
        assertThat(sent.getParseMode()).isEqualTo("HTML");
    }

    @Test
    void sendMessage_failureIsSoft() throws TelegramApiException {
        when(telegramClient.execute(any(SendMessage.class))).thenThrow(new TelegramApiException("boom"));

        assertThat(client.sendMessage(42L, "hello")).isFalse();
    }

    @Test
    void sendMessage_withButtons_attachesInlineKeyboard() throws TelegramApiException {
        ArgumentCaptor<SendMessage> captor = ArgumentCaptor.forClass(SendMessage.class);

        boolean ok = client.sendMessage(7L, "pick", List.of(new InlineButton("Yes", "yes")));

        assertThat(ok).isTrue();
        verify(telegramClient).execute(captor.capture());
        assertThat(captor.getValue().getReplyMarkup()).isInstanceOf(InlineKeyboardMarkup.class);
    }

    @Test
    void sendPoll_returnsPollId() throws TelegramApiException {
        Poll poll = mock(Poll.class);
        when(poll.getId()).thenReturn("poll-1");
        Message message = mock(Message.class);
        when(message.getPoll()).thenReturn(poll);
        when(telegramClient.execute(any(SendPoll.class))).thenReturn(message);

        String pollId = client.sendPoll(9L, "Q?", List.of("A", "B"));

        assertThat(pollId).isEqualTo("poll-1");
    }

    @Test
    void sendPoll_tooFewOptions_skips() {
        assertThat(client.sendPoll(9L, "Q?", List.of("only"))).isNull();
    }

    @Test
    void sendPoll_failureReturnsNull() throws TelegramApiException {
        when(telegramClient.execute(any(SendPoll.class))).thenThrow(new TelegramApiException("boom"));

        assertThat(client.sendPoll(9L, "Q?", List.of("A", "B"))).isNull();
    }

    @Test
    void sendQuizPoll_buildsQuizRequest() throws TelegramApiException {
        Poll poll = mock(Poll.class);
        when(poll.getId()).thenReturn("quiz-1");
        Message message = mock(Message.class);
        when(message.getPoll()).thenReturn(poll);
        ArgumentCaptor<SendPoll> captor = ArgumentCaptor.forClass(SendPoll.class);
        when(telegramClient.execute(any(SendPoll.class))).thenReturn(message);

        String pollId = client.sendQuizPoll(9L, "Q?", List.of("A", "B", "C"), 1, "because B is right");

        assertThat(pollId).isEqualTo("quiz-1");
        verify(telegramClient).execute(captor.capture());
        SendPoll sent = captor.getValue();
        assertThat(sent.getType()).isEqualTo("quiz");
        assertThat(sent.getCorrectOptionIds()).containsExactly(1);
        assertThat(sent.getExplanation()).isEqualTo("because B is right");
    }

    @Test
    void sendQuizPoll_invalidCorrectIndex_skips() throws TelegramApiException {
        assertThat(client.sendQuizPoll(9L, "Q?", List.of("A", "B"), 5, null)).isNull();

        verify(telegramClient, never()).execute(any(SendPoll.class));
    }

    @Test
    void sendQuizPoll_failureReturnsNull() throws TelegramApiException {
        when(telegramClient.execute(any(SendPoll.class))).thenThrow(new TelegramApiException("boom"));

        assertThat(client.sendQuizPoll(9L, "Q?", List.of("A", "B"), 0, null)).isNull();
    }

    @Test
    void streamMessage_buildsDraftRequest() throws TelegramApiException {
        ArgumentCaptor<SendMessageDraft> captor = ArgumentCaptor.forClass(SendMessageDraft.class);

        assertThat(client.streamMessage(42L, 7, "partial")).isTrue();

        verify(telegramClient).execute(captor.capture());
        SendMessageDraft sent = captor.getValue();
        assertThat(sent.getChatId()).isEqualTo(42L);
        assertThat(sent.getDraftId()).isEqualTo(7);
        assertThat(sent.getText()).isEqualTo("partial");
    }

    @Test
    void streamMessage_zeroDraftIdSkips() throws TelegramApiException {
        assertThat(client.streamMessage(42L, 0, "partial")).isFalse();

        verify(telegramClient, never()).execute(any(SendMessageDraft.class));
    }

    @Test
    void streamMessage_truncatesTo4096() throws TelegramApiException {
        ArgumentCaptor<SendMessageDraft> captor = ArgumentCaptor.forClass(SendMessageDraft.class);
        String big = "x".repeat(5000);

        assertThat(client.streamMessage(42L, 7, big)).isTrue();

        verify(telegramClient).execute(captor.capture());
        assertThat(captor.getValue().getText()).hasSize(4096);
    }

    @Test
    void streamMessage_failureIsSoft() throws TelegramApiException {
        when(telegramClient.execute(any(SendMessageDraft.class))).thenThrow(new TelegramApiException("boom"));

        assertThat(client.streamMessage(42L, 7, "partial")).isFalse();
    }

    @Test
    void getFile_returnsFile() throws TelegramApiException {
        File file = new File();
        file.setFilePath("documents/file_1.pdf");
        ArgumentCaptor<GetFile> captor = ArgumentCaptor.forClass(GetFile.class);
        when(telegramClient.execute(any(GetFile.class))).thenReturn(file);

        File result = client.getFile("file-1");

        assertThat(result).isSameAs(file);
        verify(telegramClient).execute(captor.capture());
        assertThat(captor.getValue().getFileId()).isEqualTo("file-1");
    }

    @Test
    void getFile_failureReturnsNull() throws TelegramApiException {
        when(telegramClient.execute(any(GetFile.class))).thenThrow(new TelegramApiException("boom"));

        assertThat(client.getFile("file-1")).isNull();
    }

    @Test
    void getFile_blankIdSkips() throws TelegramApiException {
        assertThat(client.getFile("  ")).isNull();

        verify(telegramClient, never()).execute(any(GetFile.class));
    }

    @Test
    void downloadFile_readsBytes() throws TelegramApiException {
        File file = new File();
        file.setFilePath("documents/file_1.pdf");
        InputStream stream = new ByteArrayInputStream(new byte[]{1, 2, 3});
        when(telegramClient.downloadFileAsStream(file)).thenReturn(stream);

        byte[] bytes = client.downloadFile(file);

        assertThat(bytes).containsExactly(1, 2, 3);
    }

    @Test
    void downloadFile_missingPathSkips() throws TelegramApiException {
        assertThat(client.downloadFile(new File())).isNull();

        verify(telegramClient, never()).downloadFileAsStream(any(File.class));
    }

    @Test
    void downloadFile_failureReturnsNull() throws TelegramApiException {
        File file = new File();
        file.setFilePath("documents/file_1.pdf");
        when(telegramClient.downloadFileAsStream(file)).thenThrow(new TelegramApiException("boom"));

        assertThat(client.downloadFile(file)).isNull();
    }

    @Test
    void answerCallbackQuery_executesRequest() throws TelegramApiException {
        ArgumentCaptor<AnswerCallbackQuery> captor = ArgumentCaptor.forClass(AnswerCallbackQuery.class);

        client.answerCallbackQuery("cb-1");

        verify(telegramClient).execute(captor.capture());
        assertThat(captor.getValue().getCallbackQueryId()).isEqualTo("cb-1");
    }

    @Test
    void disabled_skipsAllCalls() throws TelegramApiException {
        TelegramApiClient disabled =
                new TelegramApiClient(telegramClient, new TelegramProperties("  ", "bot", "secret", "https://example.com", 1000L));

        assertThat(disabled.isEnabled()).isFalse();
        assertThat(disabled.sendMessage(1L, "x")).isFalse();
        assertThat(disabled.sendPoll(1L, "Q?", List.of("A", "B"))).isNull();
        disabled.answerCallbackQuery("cb");

        verify(telegramClient, never()).execute(any(SendMessage.class));
    }
}
