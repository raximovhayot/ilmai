package org.aiincubator.ilmai.plan.service;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.aiincubator.ilmai.plan.domain.LessonCitation;

import java.util.List;

@Getter
@AllArgsConstructor
public final class LessonDraft {

    private final String content;
    private final List<LessonCitation> citations;
}
