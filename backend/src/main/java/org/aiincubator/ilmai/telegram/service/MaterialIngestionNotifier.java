package org.aiincubator.ilmai.telegram.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aiincubator.ilmai.ai.MaterialIngestionCompletedEvent;
import org.aiincubator.ilmai.common.i18n.MessageService;
import org.aiincubator.ilmai.common.i18n.SupportedLocale;
import org.aiincubator.ilmai.materials.MaterialDto;
import org.aiincubator.ilmai.materials.MaterialStatus;
import org.aiincubator.ilmai.materials.MaterialsApi;
import org.aiincubator.ilmai.profiles.ProfileDto;
import org.aiincubator.ilmai.profiles.ProfilesApi;
import org.aiincubator.ilmai.telegram.TelegramApi;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class MaterialIngestionNotifier {

    private final TelegramApi telegramApi;
    private final MaterialsApi materialsApi;
    private final ProfilesApi profilesApi;
    private final MessageService messageService;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMaterialIngestionCompleted(MaterialIngestionCompletedEvent event) {
        if (!telegramApi.isEnabled()) {
            return;
        }
        Optional<MaterialDto> material = materialsApi.findById(event.getMaterialId());
        if (material.isEmpty()) {
            return;
        }
        SupportedLocale locale = localeOf(profilesApi.find(event.getUserId()).orElse(null));
        String title = escapeHtml(material.get().getTitle());
        String key = event.getStatus() == MaterialStatus.READY
                ? "telegram.bot.upload.ready"
                : "telegram.bot.upload.processingFailed";
        String text = messageService.get(key, new Object[]{title}, locale.getLocale());
        try {
            telegramApi.sendMessage(event.getUserId(), text);
        } catch (RuntimeException ex) {
            log.warn("telegram ingestion notification failed for material {}: {}",
                    event.getMaterialId(), ex.toString());
        }
    }

    private SupportedLocale localeOf(ProfileDto profile) {
        if (profile == null || profile.getLocale() == null) {
            return SupportedLocale.DEFAULT;
        }
        return profile.getLocale();
    }

    private String escapeHtml(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
