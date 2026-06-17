package org.aiincubator.ilmai.agent;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = TextPart.class, name = "text"),
        @JsonSubTypes.Type(value = CitationPart.class, name = "citation"),
        @JsonSubTypes.Type(value = ActionPart.class, name = "action"),
        @JsonSubTypes.Type(value = QuizCardPart.class, name = "quiz_card"),
        @JsonSubTypes.Type(value = ErrorPart.class, name = "error"),
})
public abstract class MessagePart {
}
