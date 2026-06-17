package org.aiincubator.ilmai.telegram.service;

import org.aiincubator.ilmai.ai.MaterialIngestionCompletedEvent;
import org.aiincubator.ilmai.common.i18n.MessageService;
import org.aiincubator.ilmai.materials.MaterialDto;
import org.aiincubator.ilmai.materials.MaterialStatus;
import org.aiincubator.ilmai.materials.MaterialsApi;
import org.aiincubator.ilmai.profiles.ProfilesApi;
import org.aiincubator.ilmai.telegram.TelegramApi;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MaterialIngestionNotifierTest {

    @Mock TelegramApi telegramApi;
    @Mock MaterialsApi materialsApi;
    @Mock ProfilesApi profilesApi;
    @Mock MessageService messageService;

    @InjectMocks MaterialIngestionNotifier notifier;

    private MaterialDto material(UUID id) {
        return new MaterialDto(id, UUID.randomUUID(), UUID.randomUUID(), "Algebra",
                "text/plain", 10L, MaterialStatus.READY, 0, null, null);
    }

    @Test
    void sendsReadyMessageWhenIngestionSucceeds() {
        UUID materialId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(telegramApi.isEnabled()).thenReturn(true);
        when(materialsApi.findById(materialId)).thenReturn(Optional.of(material(materialId)));
        when(profilesApi.find(userId)).thenReturn(Optional.empty());
        when(messageService.get(eq("telegram.bot.upload.ready"), any(), any())).thenReturn("ready Algebra");

        notifier.onMaterialIngestionCompleted(
                new MaterialIngestionCompletedEvent(materialId, userId, MaterialStatus.READY));

        verify(telegramApi).sendMessage(userId, "ready Algebra");
    }

    @Test
    void sendsFailureMessageWhenIngestionFails() {
        UUID materialId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(telegramApi.isEnabled()).thenReturn(true);
        when(materialsApi.findById(materialId)).thenReturn(Optional.of(material(materialId)));
        when(profilesApi.find(userId)).thenReturn(Optional.empty());
        when(messageService.get(eq("telegram.bot.upload.processingFailed"), any(), any()))
                .thenReturn("failed Algebra");

        notifier.onMaterialIngestionCompleted(
                new MaterialIngestionCompletedEvent(materialId, userId, MaterialStatus.FAILED));

        verify(telegramApi).sendMessage(userId, "failed Algebra");
    }

    @Test
    void skipsWhenTelegramDisabled() {
        when(telegramApi.isEnabled()).thenReturn(false);

        notifier.onMaterialIngestionCompleted(
                new MaterialIngestionCompletedEvent(UUID.randomUUID(), UUID.randomUUID(), MaterialStatus.READY));

        verify(materialsApi, never()).findById(any());
        verify(telegramApi, never()).sendMessage(any(), any());
    }
}
