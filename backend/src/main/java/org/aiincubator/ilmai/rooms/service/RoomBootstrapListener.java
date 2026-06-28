package org.aiincubator.ilmai.rooms.service;

import lombok.RequiredArgsConstructor;
import org.aiincubator.ilmai.auth.UserRegisteredEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class RoomBootstrapListener {

    private final RoomService roomService;

    @EventListener
    @Transactional(propagation = Propagation.MANDATORY)
    public void onUserRegistered(UserRegisteredEvent event) {
        roomService.create(event.getUserId(), event.getFirstNameHint());
    }
}
