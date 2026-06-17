package org.aiincubator.ilmai.materials.service;


import org.aiincubator.ilmai.materials.MaterialUploadedEvent;
import org.aiincubator.ilmai.materials.domain.Material;
import org.aiincubator.ilmai.materials.domain.MaterialRepository;
import org.aiincubator.ilmai.materials.MaterialStatus;
import org.aiincubator.ilmai.materials.domain.Topic;
import org.aiincubator.ilmai.spaces.SpaceDto;
import org.aiincubator.ilmai.spaces.SpacesApi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Pageable;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MaterialReingestSchedulerTest {

    @Mock MaterialRepository materials;
    @Mock ApplicationEventPublisher publisher;
    @Mock SpacesApi spacesApi;

    private MaterialReingestProperties properties;
    private MaterialReingestScheduler scheduler;

    @BeforeEach
    void setUp() {
        properties = new MaterialReingestProperties();
        properties.setEnabled(true);
        properties.setMaxAttempts(3);
        properties.setMinFailureAge(Duration.ofMinutes(5));
        properties.setBatchSize(20);
        scheduler = new MaterialReingestScheduler(materials, publisher, properties, spacesApi);
    }

    @Test
    void retryFailed_flipsCandidatesToPendingAndPublishesUploadEvent() {
        UUID userId = UUID.randomUUID();
        UUID spaceId = UUID.randomUUID();
        Material failed = newFailedMaterial(spaceId, 1);
        when(materials.findRetryCandidates(
                eq(MaterialStatus.FAILED), eq(3), any(OffsetDateTime.class), any(Pageable.class)))
                .thenReturn(List.of(failed));
        when(spacesApi.findById(spaceId)).thenReturn(Optional.of(new SpaceDto(spaceId, userId, "My private space")));

        scheduler.retryFailed();

        assertThat(failed.getStatus()).isEqualTo(MaterialStatus.PENDING);
        assertThat(failed.getRetryCount()).isEqualTo(2);
        verify(materials).saveAndFlush(failed);
        ArgumentCaptor<MaterialUploadedEvent> captor = ArgumentCaptor.forClass(MaterialUploadedEvent.class);
        verify(publisher).publishEvent(captor.capture());
        assertThat(captor.getValue().getMaterialId()).isEqualTo(failed.getId());
        assertThat(captor.getValue().getUserId()).isEqualTo(userId);
    }

    @Test
    void retryFailed_doesNothingWhenNoCandidates() {
        when(materials.findRetryCandidates(any(), anyInt(), any(), any())).thenReturn(List.of());

        scheduler.retryFailed();

        verify(materials, never()).saveAndFlush(any());
        verifyNoInteractions(publisher);
    }

    @Test
    void retryFailed_processesMultipleCandidatesInOnePass() {
        UUID userId = UUID.randomUUID();
        UUID spaceId = UUID.randomUUID();
        Material a = newFailedMaterial(spaceId, 0);
        Material b = newFailedMaterial(spaceId, 2);
        when(materials.findRetryCandidates(
                eq(MaterialStatus.FAILED), eq(3), any(OffsetDateTime.class), any(Pageable.class)))
                .thenReturn(List.of(a, b));
        when(spacesApi.findById(spaceId)).thenReturn(Optional.of(new SpaceDto(spaceId, userId, "My private space")));

        scheduler.retryFailed();

        assertThat(a.getStatus()).isEqualTo(MaterialStatus.PENDING);
        assertThat(b.getStatus()).isEqualTo(MaterialStatus.PENDING);
        assertThat(a.getRetryCount()).isEqualTo(1);
        assertThat(b.getRetryCount()).isEqualTo(3);
        verify(materials, times(2)).saveAndFlush(any(Material.class));
        verify(publisher, times(2)).publishEvent(any(MaterialUploadedEvent.class));
    }

    private Material newFailedMaterial(UUID spaceId, int currentRetryCount) {
        Topic topic = new Topic();
        topic.setId(UUID.randomUUID());
        topic.setSpaceId(spaceId);
        topic.setName("Cloud");

        Material material = new Material();
        material.setId(UUID.randomUUID());
        material.setSpaceId(spaceId);
        material.setTopic(topic);
        material.setTitle("notes.txt");
        material.setStatus(MaterialStatus.FAILED);
        material.setContentType("text/plain");
        material.setSizeBytes(10L);
        material.setRetryCount(currentRetryCount);
        return material;
    }
}
