package org.aiincubator.ilmai.profiles.api;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.aiincubator.ilmai.common.CurrentUser;
import org.aiincubator.ilmai.common.payload.ApiResponse;
import org.aiincubator.ilmai.profiles.payload.OnboardingRequest;
import org.aiincubator.ilmai.profiles.payload.OnboardingResponse;
import org.aiincubator.ilmai.profiles.service.OnboardingService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/onboarding")
@RequiredArgsConstructor
public class OnboardingController {

    private final OnboardingService onboardingService;

    @GetMapping
    public ApiResponse<OnboardingResponse> get(@AuthenticationPrincipal CurrentUser currentUser) {
        return ApiResponse.ok(onboardingService.get(currentUser));
    }

    @PutMapping
    public ApiResponse<OnboardingResponse> submit(@AuthenticationPrincipal CurrentUser currentUser,
                                                  @Valid @RequestBody OnboardingRequest request) {
        return ApiResponse.ok(onboardingService.submit(currentUser, request));
    }
}
