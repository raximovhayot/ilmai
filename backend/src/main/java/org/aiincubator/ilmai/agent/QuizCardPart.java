package org.aiincubator.ilmai.agent;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.util.List;
import java.util.UUID;

@Getter
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
@ToString
public class QuizCardPart extends MessagePart {

    private final UUID sessionId;
    private final UUID questionId;
    private final int position;
    private final String type;
    private final String concept;
    private final String prompt;
    private final List<String> options;
    private final UUID materialId;
    private final String materialName;
    private final Integer chunkIndex;
}
