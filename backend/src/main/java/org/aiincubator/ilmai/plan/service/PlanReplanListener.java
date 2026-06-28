package org.aiincubator.ilmai.plan.service;

import lombok.RequiredArgsConstructor;
import org.aiincubator.ilmai.materials.MaterialUploadedEvent;
import org.aiincubator.ilmai.rooms.RoomGoalUpdatedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class PlanReplanListener {

    private final PlanService planService;

    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onMaterialUploaded(MaterialUploadedEvent event) {
        planService.markReplanNeeded(event.getUserId());
    }

    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onGoalUpdated(RoomGoalUpdatedEvent event) {
        planService.markReplanNeeded(event.getUserId());
    }
}
