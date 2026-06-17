package org.aiincubator.ilmai.ai.ingestion.reader;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

@Getter
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
@ToString
public final class TextMaterialPart extends MaterialPart {

    private final String text;
}
