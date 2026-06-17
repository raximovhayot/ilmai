package org.aiincubator.ilmai.spaces.api;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.aiincubator.ilmai.common.CurrentUser;
import org.aiincubator.ilmai.common.payload.ApiResponse;
import org.aiincubator.ilmai.spaces.payload.RenameSpaceRequest;
import org.aiincubator.ilmai.spaces.payload.SpaceResponse;
import org.aiincubator.ilmai.spaces.service.SpaceService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/spaces")
@RequiredArgsConstructor
public class SpaceController {

    private final SpaceService spaceService;

    @GetMapping
    public ApiResponse<List<SpaceResponse>> list(@AuthenticationPrincipal CurrentUser currentUser) {
        return ApiResponse.ok(spaceService.getAll(currentUser));
    }

    @PatchMapping("/{spaceId}")
    public ApiResponse<SpaceResponse> rename(@AuthenticationPrincipal CurrentUser currentUser,
                                             @PathVariable UUID spaceId,
                                             @Valid @RequestBody RenameSpaceRequest request) {
        return ApiResponse.ok(spaceService.rename(currentUser, spaceId, request.getName()));
    }
}
