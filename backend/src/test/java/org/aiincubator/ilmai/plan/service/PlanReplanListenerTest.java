package org.aiincubator.ilmai.plan.service;

import org.aiincubator.ilmai.materials.MaterialUploadedEvent;
import org.aiincubator.ilmai.profiles.GoalUpdatedEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PlanReplanListenerTest {

    @Mock PlanService planService;

    @InjectMocks PlanReplanListener listener;

    @Test
    void materialUploadMarksReplanNeededForTheUploader() {
        UUID user = UUID.randomUUID();

        listener.onMaterialUploaded(new MaterialUploadedEvent(UUID.randomUUID(), user));

        verify(planService).markReplanNeeded(user);
    }

    @Test
    void goalUpdateMarksReplanNeededForTheUser() {
        UUID user = UUID.randomUUID();

        listener.onGoalUpdated(new GoalUpdatedEvent(user));

        verify(planService).markReplanNeeded(user);
    }
}
