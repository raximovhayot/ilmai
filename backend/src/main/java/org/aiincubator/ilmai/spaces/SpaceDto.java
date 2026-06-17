package org.aiincubator.ilmai.spaces;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.UUID;

@Getter
@AllArgsConstructor
public class SpaceDto {

    private final UUID id;
    private final UUID userId;
    private final String name;
}
