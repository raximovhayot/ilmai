package org.aiincubator.ilmai.agent;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

@Getter
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
@ToString
public class ErrorPart extends MessagePart {

    private final String code;
    private final String message;
    private final boolean retryable;
}
