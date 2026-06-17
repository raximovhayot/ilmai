package org.aiincubator.ilmai.agent.service;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class DigestNarrationDraft {

    private final String whereYouStand;
    private final List<String> focusNextWeek;
    private final int ilmTokenCost;
}
