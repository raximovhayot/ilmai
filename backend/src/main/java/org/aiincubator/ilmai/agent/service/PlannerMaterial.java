package org.aiincubator.ilmai.agent.service;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.UUID;

@Getter
@AllArgsConstructor
public final class PlannerMaterial {

    private final int number;
    private final UUID id;
    private final String title;
}
