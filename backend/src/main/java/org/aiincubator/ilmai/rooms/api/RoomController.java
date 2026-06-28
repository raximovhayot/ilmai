package org.aiincubator.ilmai.rooms.api;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.aiincubator.ilmai.common.CurrentUser;
import org.aiincubator.ilmai.common.payload.ApiResponse;
import org.aiincubator.ilmai.rooms.payload.CreateRoomRequest;
import org.aiincubator.ilmai.rooms.payload.JoinRoomRequest;
import org.aiincubator.ilmai.rooms.payload.RenameRoomRequest;
import org.aiincubator.ilmai.rooms.payload.RoomInviteResponse;
import org.aiincubator.ilmai.rooms.payload.RoomMemberResponse;
import org.aiincubator.ilmai.rooms.payload.RoomResponse;
import org.aiincubator.ilmai.rooms.service.RoomMembershipService;
import org.aiincubator.ilmai.rooms.service.RoomService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/rooms")
@RequiredArgsConstructor
public class RoomController {

    private final RoomService roomService;
    private final RoomMembershipService roomMembershipService;

    @GetMapping
    public ApiResponse<List<RoomResponse>> list(@AuthenticationPrincipal CurrentUser currentUser) {
        return ApiResponse.ok(roomService.getAll(currentUser));
    }

    @PostMapping
    public ApiResponse<RoomResponse> create(@AuthenticationPrincipal CurrentUser currentUser,
                                            @Valid @RequestBody CreateRoomRequest request) {
        return ApiResponse.ok(roomService.createExtra(currentUser, request.getName()));
    }

    @PatchMapping("/{roomId}")
    public ApiResponse<RoomResponse> rename(@AuthenticationPrincipal CurrentUser currentUser,
                                            @PathVariable UUID roomId,
                                            @Valid @RequestBody RenameRoomRequest request) {
        return ApiResponse.ok(roomService.rename(currentUser, roomId, request.getName()));
    }

    @PostMapping("/join")
    public ApiResponse<RoomResponse> join(@AuthenticationPrincipal CurrentUser currentUser,
                                          @Valid @RequestBody JoinRoomRequest request) {
        return ApiResponse.ok(roomMembershipService.join(currentUser, request.getCode()));
    }

    @PostMapping("/{roomId}/invite")
    public ApiResponse<RoomInviteResponse> createInvite(@AuthenticationPrincipal CurrentUser currentUser,
                                                        @PathVariable UUID roomId) {
        return ApiResponse.ok(roomMembershipService.createInvite(currentUser, roomId));
    }

    @DeleteMapping("/{roomId}/invite")
    public ApiResponse<Void> revokeInvite(@AuthenticationPrincipal CurrentUser currentUser,
                                          @PathVariable UUID roomId) {
        roomMembershipService.revokeInvite(currentUser, roomId);
        return ApiResponse.ok(null);
    }

    @GetMapping("/{roomId}/members")
    public ApiResponse<List<RoomMemberResponse>> members(@AuthenticationPrincipal CurrentUser currentUser,
                                                         @PathVariable UUID roomId) {
        return ApiResponse.ok(roomMembershipService.listMembers(currentUser, roomId));
    }

    @DeleteMapping("/{roomId}/membership")
    public ApiResponse<Void> leave(@AuthenticationPrincipal CurrentUser currentUser,
                                   @PathVariable UUID roomId) {
        roomMembershipService.leave(currentUser, roomId);
        return ApiResponse.ok(null);
    }

    @DeleteMapping("/{roomId}/members/{userId}")
    public ApiResponse<Void> removeMember(@AuthenticationPrincipal CurrentUser currentUser,
                                          @PathVariable UUID roomId,
                                          @PathVariable UUID userId) {
        roomMembershipService.removeMember(currentUser, roomId, userId);
        return ApiResponse.ok(null);
    }
}
