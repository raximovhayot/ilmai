package org.aiincubator.ilmai.gaps.api;

import lombok.RequiredArgsConstructor;
import org.aiincubator.ilmai.common.CurrentUser;
import org.aiincubator.ilmai.common.payload.ApiResponse;
import org.aiincubator.ilmai.gaps.payload.GapsReportResponse;
import org.aiincubator.ilmai.gaps.service.GapsService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/gaps")
@RequiredArgsConstructor
public class GapsController {

    private final GapsService gapsService;

    @GetMapping
    public ApiResponse<GapsReportResponse> get(@AuthenticationPrincipal CurrentUser currentUser) {
        return ApiResponse.ok(gapsService.get(currentUser));
    }

    @PostMapping("/refresh")
    public ApiResponse<GapsReportResponse> refresh(@AuthenticationPrincipal CurrentUser currentUser) {
        return ApiResponse.ok(gapsService.refreshAndGet(currentUser));
    }
}
