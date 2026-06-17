package org.aiincubator.ilmai.agent.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aiincubator.ilmai.common.CurrentUser;
import org.aiincubator.ilmai.common.quota.IlmTokenReservation;
import org.aiincubator.ilmai.common.quota.QuotaService;
import org.aiincubator.ilmai.gaps.GapItemDto;
import org.aiincubator.ilmai.gaps.GapsApi;
import org.aiincubator.ilmai.gaps.GapsReportDto;
import org.aiincubator.ilmai.materials.MaterialDto;
import org.aiincubator.ilmai.materials.MaterialsApi;
import org.aiincubator.ilmai.plan.LearningPlanDto;
import org.aiincubator.ilmai.plan.PlanApi;
import org.aiincubator.ilmai.profiles.ProfileDto;
import org.aiincubator.ilmai.profiles.ProfilesApi;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class BuildPlanTool {

    static final int PLAN_BUILD_ESTIMATE_ILM_TOKENS = 8;
    static final int DEFAULT_PLAN_DAYS = 14;
    static final int MAX_PLAN_DAYS = 30;
    static final int MAX_WEAK_CONCEPTS = 5;

    private final ProfilesApi profilesApi;
    private final MaterialsApi materialsApi;
    private final GapsApi gapsApi;
    private final Planner planner;
    private final PlanApi planApi;
    private final PlanViewFactory planViewFactory;
    private final QuotaService quotaService;

    @Tool(description = "Build a day-by-day study plan for the current user from their own goal, uploaded materials, "
            + "and known weak concepts, then save it as their active plan (replacing any previous one). Call this when "
            + "the user asks for a plan, a schedule, or how to prepare. Returns the saved plan. If hasPlan is false the "
            + "plan could not be built (usually because no materials are ready) - ask the user to upload material first.")
    public PlanView buildPlan(
            @ToolParam(required = false, description = "How many days the plan should span. Defaults to the days left "
                    + "until the deadline, or 14 when there is no deadline.")
            Integer days,
            @ToolParam(required = false, description = "Language to write the plan in: 'en', 'ru', or 'uz'. Use the "
                    + "language the user is writing in.")
            String language,
            ToolContext toolContext) {
        CurrentUser currentUser = AgentToolContext.requireCurrentUser(toolContext);
        UUID userId = currentUser.getUserId();

        List<MaterialDto> ready = materialsApi.findReadyForUser(userId);
        if (ready.isEmpty() || !planner.isAvailable()) {
            log.debug("agent.buildPlan not-ready user={} materials={} planner={}",
                    userId, ready.size(), planner.isAvailable());
            return emptyView();
        }

        List<PlannerMaterial> materials = new ArrayList<>();
        for (int i = 0; i < ready.size(); i++) {
            MaterialDto material = ready.get(i);
            materials.add(new PlannerMaterial(i + 1, material.getId(), material.getTitle()));
        }

        ProfileDto profile = profilesApi.find(userId).orElse(null);
        LocalDate today = LocalDate.now();
        LocalDate targetDate = profile == null ? null : profile.getTargetDate();
        Integer dailyStudyMinutes = profile == null ? null : profile.getDailyStudyMinutes();
        String goal = profile == null ? null : profile.getGoal();
        Integer daysUntilDeadline = daysUntilDeadline(today, targetDate);
        int planDays = resolvePlanDays(days, daysUntilDeadline);

        PlannerBrief brief = new PlannerBrief(language, goal, targetDate, daysUntilDeadline,
                planDays, dailyStudyMinutes, weakConcepts(currentUser));

        if (!quotaService.canSpend(userId, PLAN_BUILD_ESTIMATE_ILM_TOKENS)) {
            log.debug("agent.buildPlan skipped (quota) user={}", userId);
            return emptyView();
        }
        IlmTokenReservation reservation = quotaService.reserve(userId, PLAN_BUILD_ESTIMATE_ILM_TOKENS);
        boolean committed = false;
        try {
            PlanDraft draft = planner.plan(brief, materials, today);
            if (draft == null) {
                return emptyView();
            }
            LearningPlanDto saved = planApi.savePlan(currentUser, goal, targetDate, draft.getSteps());
            quotaService.commit(reservation, draft.getIlmTokenCost());
            committed = true;
            return planViewFactory.toView(userId, saved, saved.getSteps());
        } finally {
            if (!committed) {
                quotaService.refund(reservation);
            }
        }
    }

    private List<String> weakConcepts(CurrentUser currentUser) {
        GapsReportDto report = gapsApi.refreshAndGet(currentUser).orElse(null);
        if (report == null || report.getGaps() == null) {
            return List.of();
        }
        List<String> concepts = new ArrayList<>();
        for (GapItemDto gap : report.getGaps()) {
            if (gap.getConcept() != null && !gap.getConcept().isBlank()) {
                concepts.add(gap.getConcept());
            }
            if (concepts.size() >= MAX_WEAK_CONCEPTS) {
                break;
            }
        }
        return concepts;
    }

    private Integer daysUntilDeadline(LocalDate today, LocalDate targetDate) {
        if (targetDate == null || !targetDate.isAfter(today)) {
            return null;
        }
        return (int) ChronoUnit.DAYS.between(today, targetDate);
    }

    private int resolvePlanDays(Integer requested, Integer daysUntilDeadline) {
        int planDays;
        if (requested != null && requested > 0) {
            planDays = requested;
        } else if (daysUntilDeadline != null && daysUntilDeadline > 0) {
            planDays = daysUntilDeadline;
        } else {
            planDays = DEFAULT_PLAN_DAYS;
        }
        return Math.min(planDays, MAX_PLAN_DAYS);
    }

    private PlanView emptyView() {
        return new PlanView(false, null, null, List.of(), false);
    }
}
