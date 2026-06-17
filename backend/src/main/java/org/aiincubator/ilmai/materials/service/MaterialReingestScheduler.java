package org.aiincubator.ilmai.materials.service;


import org.aiincubator.ilmai.materials.MaterialUploadedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aiincubator.ilmai.materials.domain.Material;
import org.aiincubator.ilmai.materials.domain.MaterialRepository;
import org.aiincubator.ilmai.materials.MaterialStatus;
import org.aiincubator.ilmai.spaces.SpaceDto;
import org.aiincubator.ilmai.spaces.SpacesApi;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@EnableConfigurationProperties(MaterialReingestProperties.class)
@ConditionalOnProperty(name = "ingestion.retry.enabled", havingValue = "true", matchIfMissing = true)
public class MaterialReingestScheduler {

    private final MaterialRepository materials;
    private final ApplicationEventPublisher publisher;
    private final MaterialReingestProperties properties;
    private final SpacesApi spacesApi;

    @Scheduled(
            fixedDelayString = "${ingestion.retry.fixed-delay:PT1M}",
            initialDelayString = "${ingestion.retry.initial-delay:PT1M}"
    )
    @Transactional
    public void retryFailed() {
        OffsetDateTime cutoff = OffsetDateTime.now().minus(properties.getMinFailureAge());
        List<Material> candidates = materials.findRetryCandidates(
                MaterialStatus.FAILED,
                properties.getMaxAttempts(),
                cutoff,
                PageRequest.of(0, properties.getBatchSize())
        );
        for (Material material : candidates) {
            resetForRetry(material);
        }
    }

    private void resetForRetry(Material material) {
        int attempt = material.getRetryCount() + 1;
        material.setRetryCount(attempt);
        material.setStatus(MaterialStatus.PENDING);
        materials.saveAndFlush(material);
        UUID spaceId = material.getSpaceId();
        UUID userId = spacesApi.findById(spaceId).map(SpaceDto::getUserId).orElse(null);
        if (userId == null) {
            log.warn("Skipping retry for material {}: space {} not found", material.getId(), spaceId);
            return;
        }
        log.info("Scheduling retry {} of {} for material {} (user {})",
                attempt, properties.getMaxAttempts(), material.getId(), userId);
        publisher.publishEvent(new MaterialUploadedEvent(material.getId(), userId));
    }
}
