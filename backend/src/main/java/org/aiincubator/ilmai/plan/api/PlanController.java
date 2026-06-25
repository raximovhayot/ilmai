package org.aiincubator.ilmai.plan.api;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.aiincubator.ilmai.common.CurrentUser;
import org.aiincubator.ilmai.common.payload.ApiResponse;
import org.aiincubator.ilmai.plan.payload.CompleteStepRequest;
import org.aiincubator.ilmai.plan.payload.LearningPlanResponse;
import org.aiincubator.ilmai.plan.payload.StepLessonResponse;
import org.aiincubator.ilmai.plan.payload.UpdatePlanStatusRequest;
import org.aiincubator.ilmai.plan.service.PlanService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/plan")
@RequiredArgsConstructor
public class PlanController {

    private final PlanService planService;

    @GetMapping
    public ApiResponse<LearningPlanResponse> get(@AuthenticationPrincipal CurrentUser currentUser) {
        return ApiResponse.ok(planService.getActivePlanResponse(currentUser));
    }

    @GetMapping("/all")
    public ApiResponse<List<LearningPlanResponse>> list(@AuthenticationPrincipal CurrentUser currentUser) {
        return ApiResponse.ok(planService.listPlansResponse(currentUser));
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

    @PostMapping("/{planId}/steps/{dayIndex}/complete")
    public ApiResponse<LearningPlanResponse> completePlanStep(@AuthenticationPrincipal CurrentUser currentUser,
                                                              @PathVariable UUID planId,
                                                              @PathVariable int dayIndex) {
        return ApiResponse.ok(planService.completeStepResponse(currentUser, planId, dayIndex));
    }

    @PostMapping("/{planId}/steps/{dayIndex}/lesson")
    public ApiResponse<StepLessonResponse> planLesson(@AuthenticationPrincipal CurrentUser currentUser,
                                                      @PathVariable UUID planId,
                                                      @PathVariable int dayIndex,
                                                      @RequestParam(defaultValue = "false") boolean regenerate) {
        return ApiResponse.ok(planService.generateLessonResponse(currentUser, planId, dayIndex, regenerate));
    }

    @PostMapping("/{planId}/steps/{dayIndex}/{orderInDay}/complete")
    public ApiResponse<LearningPlanResponse> completePlanTask(@AuthenticationPrincipal CurrentUser currentUser,
                                                             @PathVariable UUID planId,
                                                             @PathVariable int dayIndex,
                                                             @PathVariable int orderInDay,
                                                             @RequestBody(required = false) CompleteStepRequest request) {
        return ApiResponse.ok(
                planService.completeTaskResponse(currentUser, planId, dayIndex, orderInDay, request));
    }

    @PostMapping("/{planId}/steps/{dayIndex}/{orderInDay}/lesson")
    public ApiResponse<StepLessonResponse> planTaskLesson(@AuthenticationPrincipal CurrentUser currentUser,
                                                          @PathVariable UUID planId,
                                                          @PathVariable int dayIndex,
                                                          @PathVariable int orderInDay,
                                                          @RequestParam(defaultValue = "false") boolean regenerate) {
        return ApiResponse.ok(
                planService.generateLessonResponse(currentUser, planId, dayIndex, orderInDay, regenerate));
    }

    @PatchMapping("/{planId}")
    public ApiResponse<LearningPlanResponse> updateStatus(@AuthenticationPrincipal CurrentUser currentUser,
                                                          @PathVariable UUID planId,
                                                          @Valid @RequestBody UpdatePlanStatusRequest request) {
        return ApiResponse.ok(planService.updatePlanStatus(currentUser, planId, request.getStatus()));
    }

    @DeleteMapping("/{planId}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal CurrentUser currentUser,
                                       @PathVariable UUID planId) {
        planService.deletePlan(currentUser, planId);
        return ResponseEntity.noContent().build();
    }
}
