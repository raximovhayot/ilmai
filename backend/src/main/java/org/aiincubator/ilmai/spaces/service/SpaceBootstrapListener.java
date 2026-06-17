package org.aiincubator.ilmai.spaces.service;

import lombok.RequiredArgsConstructor;
import org.aiincubator.ilmai.auth.UserRegisteredEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class SpaceBootstrapListener {

    private final SpaceService spaceService;

    @EventListener
    @Transactional(propagation = Propagation.MANDATORY)
    public void onUserRegistered(UserRegisteredEvent event) {
        spaceService.create(event.getUserId(), event.getFirstNameHint());
    }
}
