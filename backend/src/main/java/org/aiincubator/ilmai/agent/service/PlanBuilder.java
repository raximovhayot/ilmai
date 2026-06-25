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
import org.aiincubator.ilmai.plan.PlanActivity;
import org.aiincubator.ilmai.plan.PlanApi;
import org.aiincubator.ilmai.plan.PlanStepInput;
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
            List<PlanStepInput> tasks = expandTasks(draft.getSteps(), resolvedLanguage);
            LearningPlanDto saved = planApi.savePlan(currentUser, goal, targetDate, tasks);
            quotaService.commit(reservation, draft.getIlmTokenCost());
            committed = true;
            return Optional.of(saved);
        } finally {
            if (!committed) {
                quotaService.refund(reservation);
            }
        }
    }

    private List<PlanStepInput> expandTasks(List<PlanStepInput> steps, String language) {
        if (steps == null || steps.isEmpty()) {
            return steps;
        }
        String lang = normalizeLang(language);
        List<PlanStepInput> expanded = new ArrayList<>();
        PlanStepInput last = steps.get(steps.size() - 1);
        for (PlanStepInput step : steps) {
            expanded.add(step);
            PlanActivity activity = step.getActivity();
            if (activity == PlanActivity.READ || activity == PlanActivity.REVIEW) {
                expanded.add(new PlanStepInput(step.getDayIndex(), step.getScheduledDate(),
                        quizTitle(lang, step.getTitle()), PlanActivity.QUIZ, step.getMaterialIds(), null));
            }
        }
        expanded.add(new PlanStepInput(last.getDayIndex(), last.getScheduledDate(),
                independentTitle(lang), PlanActivity.INDEPENDENT, last.getMaterialIds(), independentNote(lang)));
        return expanded;
    }

    private static String normalizeLang(String language) {
        if (language == null || language.isBlank()) {
            return "en";
        }
        String lang = language.trim().toLowerCase(Locale.ROOT);
        if (lang.startsWith("ru")) {
            return "ru";
        }
        if (lang.startsWith("uz")) {
            return "uz";
        }
        return "en";
    }

    private static String quizTitle(String lang, String readTitle) {
        String base = readTitle == null ? "" : readTitle.trim();
        return switch (lang) {
            case "ru" -> "Тест — " + base;
            case "uz" -> "Test — " + base;
            default -> "Quiz — " + base;
        };
    }

    private static String independentTitle(String lang) {
        return switch (lang) {
            case "ru" -> "Самостоятельная практика";
            case "uz" -> "Mustaqil mashq";
            default -> "Independent practice";
        };
    }

    private static String independentNote(String lang) {
        return switch (lang) {
            case "ru" -> "Примените изученное своими словами и напишите короткую рефлексию.";
            case "uz" -> "O‘rganganlaringizni o‘z so‘zlaringiz bilan qo‘llang va qisqacha mulohaza yozing.";
            default -> "Apply what you learned in your own words, then write a short reflection.";
        };
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
