package org.aiincubator.ilmai.profiles.api;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.aiincubator.ilmai.common.CurrentUser;
import org.aiincubator.ilmai.common.payload.ApiResponse;
import org.aiincubator.ilmai.profiles.payload.ProfileResponse;
import org.aiincubator.ilmai.profiles.payload.UpdateProfileRequest;
import org.aiincubator.ilmai.profiles.service.ProfileService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/profile")
@RequiredArgsConstructor
public class ProfileController {

    private final ProfileService profileService;

    @GetMapping
    public ApiResponse<ProfileResponse> get(@AuthenticationPrincipal CurrentUser currentUser) {
        return ApiResponse.ok(profileService.get(currentUser));
    }

    @PutMapping
    public ApiResponse<ProfileResponse> update(@AuthenticationPrincipal CurrentUser currentUser,
                                               @Valid @RequestBody UpdateProfileRequest request) {
        return ApiResponse.ok(profileService.update(currentUser, request));
    }
}
