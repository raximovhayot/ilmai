package org.aiincubator.ilmai.agent.service;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ChatMemorySummaryDraft {

    private final String summary;
    private final int ilmTokenCost;
}
