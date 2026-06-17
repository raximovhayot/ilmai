package org.aiincubator.ilmai.agent;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

@Getter
@EqualsAndHashCode(callSuper = false)
@ToString
public class TextPart extends MessagePart {

    private final String text;
    private final TextConfidence confidence;

    public TextPart(String text) {
        this(text, TextConfidence.HIGH);
    }

    public TextPart(String text, TextConfidence confidence) {
        this.text = text;
        this.confidence = confidence == null ? TextConfidence.HIGH : confidence;
    }
}
