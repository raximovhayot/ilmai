package org.aiincubator.ilmai.telegram.service;

import lombok.Getter;
import org.aiincubator.ilmai.agent.CitationPart;
import org.aiincubator.ilmai.agent.ErrorPart;
import org.aiincubator.ilmai.agent.MessagePart;
import org.aiincubator.ilmai.agent.QuizCardPart;
import org.aiincubator.ilmai.agent.TextPart;

import java.util.ArrayList;
import java.util.List;

@Getter
class GroupedMessageParts {

    private final List<TextPart> texts = new ArrayList<>();
    private final List<CitationPart> citations = new ArrayList<>();
    private final List<QuizCardPart> quizCards = new ArrayList<>();
    private final List<ErrorPart> errors = new ArrayList<>();

    GroupedMessageParts(List<MessagePart> parts) {
        for (MessagePart part : parts) {
            if (part instanceof TextPart textPart) {
                texts.add(textPart);
            } else if (part instanceof CitationPart citationPart) {
                citations.add(citationPart);
            } else if (part instanceof QuizCardPart quizCardPart) {
                quizCards.add(quizCardPart);
            } else if (part instanceof ErrorPart errorPart) {
                errors.add(errorPart);
            }
        }
    }
}
