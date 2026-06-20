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
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class PlanBuilder {

    public static final int PLAN_BUILD_ESTIMATE_ILM_TOKENS = 8;
    static final int DEFAULT_PLAN_DAYS = 14;
    static final int MAX_PLAN_DAYS = 30;
    static final int MAX_WEAK_CONCEPTS = 5;

    private final ProfilesApi profilesApi;
    private final MaterialsApi materialsApi;
    private final GapsApi gapsApi;
    private final Planner planner;
    private final PlanApi planApi;
    private final QuotaService quotaService;

    public Optional<LearningPlanDto> build(UUID userId, Integer days, String language) {
        CurrentUser currentUser = new CurrentUser(userId);

        List<MaterialDto> ready = materialsApi.findReadyForUser(userId);
        if (ready.isEmpty() || !planner.isAvailable()) {
            log.debug("plan.build not-ready user={} materials={} planner={}",
                    userId, ready.size(), planner.isAvailable());
            return Optional.empty();
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
        String resolvedLanguage = resolveLanguage(language, profile);
        Integer daysUntilDeadline = daysUntilDeadline(today, targetDate);
        int planDays = resolvePlanDays(days, daysUntilDeadline);

        PlannerBrief brief = new PlannerBrief(resolvedLanguage, goal, targetDate, daysUntilDeadline,
                planDays, dailyStudyMinutes, weakConcepts(currentUser));

        if (!quotaService.canSpend(userId, PLAN_BUILD_ESTIMATE_ILM_TOKENS)) {
            log.debug("plan.build skipped (quota) user={}", userId);
            return Optional.empty();
        }
        IlmTokenReservation reservation = quotaService.reserve(userId, PLAN_BUILD_ESTIMATE_ILM_TOKENS);
        boolean committed = false;
        try {
            PlanDraft draft = planner.plan(brief, materials, today);
            if (draft == null) {
                return Optional.empty();
            }
            LearningPlanDto saved = planApi.savePlan(currentUser, goal, targetDate, draft.getSteps());
            quotaService.commit(reservation, draft.getIlmTokenCost());
            committed = true;
            return Optional.of(saved);
        } finally {
            if (!committed) {
                quotaService.refund(reservation);
            }
        }
    }

    private String resolveLanguage(String language, ProfileDto profile) {
        if (language != null && !language.isBlank()) {
            return language;
        }
        if (profile != null && profile.getLocale() != null) {
            return profile.getLocale().name().toLowerCase(Locale.ROOT);
        }
        return null;
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
}
