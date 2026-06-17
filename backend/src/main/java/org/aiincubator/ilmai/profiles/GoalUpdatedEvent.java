package org.aiincubator.ilmai.profiles;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.UUID;

@Getter
@AllArgsConstructor
public class GoalUpdatedEvent {

    private final UUID userId;
}
