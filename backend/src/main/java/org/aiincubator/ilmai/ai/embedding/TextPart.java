package org.aiincubator.ilmai.ai.embedding;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

@Getter
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
@ToString
public final class TextPart extends MultimodalPart {

    private final String text;
}
