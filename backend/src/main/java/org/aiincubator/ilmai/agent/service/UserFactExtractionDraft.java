package org.aiincubator.ilmai.agent.service;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class UserFactExtractionDraft {

    private final List<String> facts;
    private final int ilmTokenCost;
}
