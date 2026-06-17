package org.aiincubator.ilmai.agent.service;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public final class DueReviewsView {

    private final boolean hasDue;
    private final int count;
    private final List<String> concepts;
}
