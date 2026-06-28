package org.aiincubator.ilmai.rooms.payload;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RoomMemberResponse {

    private UUID userId;
    private String username;
    private String role;
    private boolean self;
}
