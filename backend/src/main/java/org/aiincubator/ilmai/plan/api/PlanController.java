package org.aiincubator.ilmai.plan.api;

import lombok.RequiredArgsConstructor;
import org.aiincubator.ilmai.common.CurrentUser;
import org.aiincubator.ilmai.common.payload.ApiResponse;
import org.aiincubator.ilmai.plan.payload.LearningPlanResponse;
import org.aiincubator.ilmai.plan.payload.StepLessonResponse;
import org.aiincubator.ilmai.plan.service.PlanService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/plan")
@RequiredArgsConstructor
public class PlanController {

    private final PlanService planService;

    @GetMapping
    public ApiResponse<LearningPlanResponse> get(@AuthenticationPrincipal CurrentUser currentUser) {
        return ApiResponse.ok(planService.getActivePlanResponse(currentUser));
    }

    @PostMapping("/steps/{dayIndex}/complete")
    public ApiResponse<LearningPlanResponse> completeStep(@AuthenticationPrincipal CurrentUser currentUser,
                                                          @PathVariable int dayIndex) {
        return ApiResponse.ok(planService.completeStepResponse(currentUser, dayIndex));
    }

    @PostMapping("/steps/{dayIndex}/lesson")
    public ApiResponse<StepLessonResponse> lesson(@AuthenticationPrincipal CurrentUser currentUser,
                                                  @PathVariable int dayIndex,
                                                  @RequestParam(defaultValue = "false") boolean regenerate) {
        return ApiResponse.ok(planService.generateLessonResponse(currentUser, dayIndex, regenerate));
    }
}
