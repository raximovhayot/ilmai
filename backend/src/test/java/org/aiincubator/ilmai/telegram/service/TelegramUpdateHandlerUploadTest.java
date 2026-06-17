package org.aiincubator.ilmai.telegram.service;

import org.aiincubator.ilmai.common.i18n.MessageService;
import org.aiincubator.ilmai.materials.MaterialDto;
import org.aiincubator.ilmai.materials.MaterialStatus;
import org.aiincubator.ilmai.materials.MaterialsApi;
import org.aiincubator.ilmai.profiles.ProfilesApi;
import org.aiincubator.ilmai.telegram.config.TelegramProperties;
import org.aiincubator.ilmai.telegram.domain.TelegramQuizPollRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.telegram.telegrambots.meta.api.objects.Document;
import org.telegram.telegrambots.meta.api.objects.File;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.chat.Chat;
import org.telegram.telegrambots.meta.api.objects.message.Message;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TelegramUpdateHandlerUploadTest {

    private static final long CHAT_ID = 555L;

    @Mock TelegramProperties properties;
    @Mock TelegramService telegramService;
    @Mock TelegramApiClient telegramApiClient;
    @Mock TelegramMessageFlattener flattener;
    @Mock org.aiincubator.ilmai.agent.AgentApi agentApi;
    @Mock org.aiincubator.ilmai.plan.PlanApi planApi;
    @Mock org.aiincubator.ilmai.streaks.StreaksApi streaksApi;
    @Mock ProfilesApi profilesApi;
    @Mock MessageService messageService;
    @Mock org.aiincubator.ilmai.quiz.QuizApi quizApi;
    @Mock TelegramQuizPollRepository pollRepository;
    @Mock MaterialsApi materialsApi;

    private TelegramUpdateHandler handler;

    @BeforeEach
    void setUp() {
        handler = new TelegramUpdateHandler(properties, telegramService, telegramApiClient, flattener,
                agentApi, planApi, streaksApi, profilesApi, messageService, quizApi, pollRepository, materialsApi);
        when(messageService.get(any(), any(), any())).thenReturn("ok");
        when(profilesApi.find(any())).thenReturn(Optional.empty());
    }

    @Test
    void documentUpload_ingestsForLinkedUserOnly() {
        UUID userA = UUID.randomUUID();
        when(telegramService.findLinkedUser(CHAT_ID)).thenReturn(Optional.of(userA));
        File file = new File();
        file.setFilePath("documents/notes.pdf");
        when(telegramApiClient.getFile("file-1")).thenReturn(file);
        when(telegramApiClient.downloadFile(file)).thenReturn(new byte[]{1, 2, 3});
        when(materialsApi.ingestUpload(eq(userA), eq("notes.pdf"), eq("application/pdf"), any()))
                .thenReturn(materialDto("notes.pdf"));

        handler.handleUpdate(documentUpdate("file-1", "notes.pdf", "application/pdf"));

        ArgumentCaptor<UUID> userCaptor = ArgumentCaptor.forClass(UUID.class);
        verify(materialsApi).ingestUpload(userCaptor.capture(), eq("notes.pdf"), eq("application/pdf"), any());
        assertThat(userCaptor.getValue()).isEqualTo(userA);
    }

    @Test
    void documentUpload_unlinkedChatDoesNotIngest() {
        when(telegramService.findLinkedUser(CHAT_ID)).thenReturn(Optional.empty());

        handler.handleUpdate(documentUpdate("file-1", "notes.pdf", "application/pdf"));

        verify(materialsApi, never()).ingestUpload(any(), any(), any(), any());
        verify(telegramApiClient, never()).getFile(any());
    }

    @Test
    void documentUpload_downloadFailureReportsAndSkipsIngest() {
        UUID userA = UUID.randomUUID();
        when(telegramService.findLinkedUser(CHAT_ID)).thenReturn(Optional.of(userA));
        File file = new File();
        file.setFilePath("documents/notes.pdf");
        when(telegramApiClient.getFile("file-1")).thenReturn(file);
        when(telegramApiClient.downloadFile(file)).thenReturn(null);

        handler.handleUpdate(documentUpdate("file-1", "notes.pdf", "application/pdf"));

        verify(materialsApi, never()).ingestUpload(any(), any(), any(), any());
        verify(telegramApiClient).sendMessage(anyLong(), any());
    }

    private Update documentUpdate(String fileId, String fileName, String mimeType) {
        Document document = Document.builder()
                .fileId(fileId)
                .fileName(fileName)
                .mimeType(mimeType)
                .build();
        Message message = Message.builder()
                .chat(Chat.builder().id(CHAT_ID).type("private").build())
                .document(document)
                .build();
        Update update = new Update();
        update.setMessage(message);
        return update;
    }

    private MaterialDto materialDto(String title) {
        return new MaterialDto(UUID.randomUUID(), null, UUID.randomUUID(), title,
                "application/pdf", 3L, MaterialStatus.PENDING, 0, null, null);
    }
}
