package org.aiincubator.ilmai.agent;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.util.Map;

@Getter
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
@ToString
public class ActionPart extends MessagePart {

    private final String action;
    private final String label;
    private final Map<String, Object> payload;
}
