package org.aiincubator.ilmai.plan.service;

import lombok.RequiredArgsConstructor;
import org.aiincubator.ilmai.common.CurrentUser;
import org.aiincubator.ilmai.common.quota.IlmTokenReservation;
import org.aiincubator.ilmai.common.quota.QuotaService;
import org.aiincubator.ilmai.plan.PlanActivity;
import org.aiincubator.ilmai.plan.PlanStatus;
import org.aiincubator.ilmai.plan.PlanStepInput;
import org.aiincubator.ilmai.plan.domain.LearningPlan;
import org.aiincubator.ilmai.plan.domain.LearningPlanRepository;
import org.aiincubator.ilmai.plan.domain.PlanStep;
import org.aiincubator.ilmai.plan.payload.CompleteStepRequest;
import org.aiincubator.ilmai.plan.payload.LearningPlanResponse;
import org.aiincubator.ilmai.plan.payload.StepLessonResponse;
import org.aiincubator.ilmai.profiles.ProfileDto;
import org.aiincubator.ilmai.profiles.ProfilesApi;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PlanService {

    static final ZoneId DEFAULT_ZONE = ZoneId.of("Asia/Tashkent");

    static final int LESSON_ESTIMATE_ILM_TOKENS = 8;

    static final int QUIZ_PASS_SCORE = 70;

    private final LearningPlanRepository learningPlanRepository;
    private final ProfilesApi profilesApi;
    private final PlanMapper planMapper;
    private final PlanLessonGenerator lessonGenerator;
    private final QuotaService quotaService;
    private final Clock clock;

    @Transactional
    public LearningPlan replaceActivePlan(UUID userId, String goal, LocalDate targetDate, List<PlanStepInput> steps) {
        List<LearningPlan> active = learningPlanRepository.findByUserIdAndStatus(userId, PlanStatus.ACTIVE);
        UUID goalId = resolveGoalId(active, goal);
        for (LearningPlan existing : active) {
            if (goalId.equals(existing.getGoalId())) {
                existing.setStatus(PlanStatus.SUPERSEDED);
            }
        }
        LearningPlan plan = new LearningPlan();
        plan.setUserId(userId);
        plan.setGoalId(goalId);
        plan.setGoal(goal);
        plan.setTargetDate(targetDate);
        plan.setStatus(PlanStatus.ACTIVE);
        if (steps != null) {
            Map<Integer, Integer> orderByDay = new HashMap<>();
            for (PlanStepInput input : steps) {
                int order = orderByDay.merge(input.getDayIndex(), 0, (existing, ignored) -> existing + 1);
                plan.getSteps().add(toStep(plan, input, order));
            }
        }
        return learningPlanRepository.save(plan);
    }

    private static UUID resolveGoalId(List<LearningPlan> activePlans, String goal) {
        String normalized = normalizeGoal(goal);
        for (LearningPlan existing : activePlans) {
            if (normalized.equals(normalizeGoal(existing.getGoal()))) {
                return existing.getGoalId();
            }
        }
        return UUID.randomUUID();
    }

    private static String normalizeGoal(String goal) {
        return goal == null ? "" : goal.strip().toLowerCase(Locale.ROOT);
    }

    @Transactional(readOnly = true)
    public Optional<LearningPlan> findActivePlan(UUID userId) {
        return learningPlanRepository.findFirstByUserIdAndStatusOrderByCreatedAtDesc(userId, PlanStatus.ACTIVE);
    }

    @Transactional
    public Optional<LearningPlan> completeStep(UUID userId, int dayIndex) {
        Optional<LearningPlan> active =
                learningPlanRepository.findFirstByUserIdAndStatusOrderByCreatedAtDesc(userId, PlanStatus.ACTIVE);
        active.ifPresent(plan -> markStepDone(plan, dayIndex));
        return active;
    }

    @Transactional(readOnly = true)
    public LearningPlanResponse getActivePlanResponse(CurrentUser currentUser) {
        return findActivePlan(currentUser.getUserId())
                .map(planMapper::toResponse)
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public List<LearningPlanResponse> listPlansResponse(CurrentUser currentUser) {
        return learningPlanRepository
                .findByUserIdAndStatusInOrderByCreatedAtAsc(
                        currentUser.getUserId(), EnumSet.of(PlanStatus.ACTIVE, PlanStatus.PAUSED))
                .stream()
                .map(planMapper::toResponse)
                .toList();
    }

    @Transactional
    public LearningPlanResponse completeStepResponse(CurrentUser currentUser, int dayIndex) {
        return completeStep(currentUser.getUserId(), dayIndex)
                .map(planMapper::toResponse)
                .orElse(null);
    }

    @Transactional
    public LearningPlanResponse completeStepResponse(CurrentUser currentUser, UUID planId, int dayIndex) {
        LearningPlan plan = requireOwnedPlan(currentUser, planId);
        markStepDone(plan, dayIndex);
        return planMapper.toResponse(plan);
    }

    @Transactional
    public LearningPlanResponse completeTaskResponse(CurrentUser currentUser, UUID planId, int dayIndex,
                                                    int orderInDay, CompleteStepRequest request) {
        LearningPlan plan = requireOwnedPlan(currentUser, planId);
        PlanStep step = findStep(plan, dayIndex, orderInDay);
        markTaskDone(step, request);
        return planMapper.toResponse(plan);
    }

    private void markTaskDone(PlanStep step, CompleteStepRequest request) {
        if (step.isDone()) {
            return;
        }
        PlanActivity activity = step.getActivity() == null ? PlanActivity.READ : step.getActivity();
        switch (activity) {
            case READ, REVIEW -> {
                if (!hasLesson(step)) {
                    throw new PlanException(PlanException.Reason.PLAN_STEP_NOT_COMPLETABLE);
                }
            }
            case QUIZ -> {
                Integer score = request == null ? null : request.getQuizScore();
                if (score == null || score < QUIZ_PASS_SCORE) {
                    throw new PlanException(PlanException.Reason.PLAN_STEP_NOT_COMPLETABLE);
                }
                step.setQuizScore(score);
            }
            case INDEPENDENT -> {
                String note = request == null ? null : request.getReflectionNote();
                if (note == null || note.isBlank()) {
                    throw new PlanException(PlanException.Reason.PLAN_STEP_NOT_COMPLETABLE);
                }
                step.setReflectionNote(note.strip());
            }
        }
        step.setDone(true);
        step.setCompletedAt(OffsetDateTime.now(clock));
    }

    private PlanStep findStep(LearningPlan plan, int dayIndex, int orderInDay) {
        return plan.getSteps().stream()
                .filter(s -> s.getDayIndex() == dayIndex && s.getOrderInDay() == orderInDay)
                .findFirst()
                .orElseThrow(() -> new PlanException(PlanException.Reason.PLAN_STEP_NOT_FOUND));
    }

    @Transactional
    public LearningPlanResponse updatePlanStatus(CurrentUser currentUser, UUID planId, PlanStatus status) {
        if (status != PlanStatus.ACTIVE && status != PlanStatus.PAUSED && status != PlanStatus.COMPLETED) {
            throw new PlanException(PlanException.Reason.PLAN_STATUS_INVALID);
        }
        LearningPlan plan = requireOwnedPlan(currentUser, planId);
        plan.setStatus(status);
        return planMapper.toResponse(plan);
    }

    @Transactional
    public void deletePlan(CurrentUser currentUser, UUID planId) {
        LearningPlan plan = requireOwnedPlan(currentUser, planId);
        learningPlanRepository.delete(plan);
    }

    @Transactional
    public StepLessonResponse generateLessonResponse(CurrentUser currentUser, int dayIndex, boolean regenerate) {
        LearningPlan plan = findActivePlan(currentUser.getUserId())
                .orElseThrow(() -> new PlanException(PlanException.Reason.PLAN_NOT_FOUND));
        PlanStep step = plan.getSteps().stream()
                .filter(s -> s.getDayIndex() == dayIndex)
                .findFirst()
                .orElseThrow(() -> new PlanException(PlanException.Reason.PLAN_STEP_NOT_FOUND));
        return generateLesson(currentUser.getUserId(), step, regenerate);
    }

    @Transactional
    public StepLessonResponse generateLessonResponse(CurrentUser currentUser, UUID planId, int dayIndex,
                                                     boolean regenerate) {
        LearningPlan plan = requireOwnedPlan(currentUser, planId);
        PlanStep step = plan.getSteps().stream()
                .filter(s -> s.getDayIndex() == dayIndex)
                .findFirst()
                .orElseThrow(() -> new PlanException(PlanException.Reason.PLAN_STEP_NOT_FOUND));
        return generateLesson(currentUser.getUserId(), step, regenerate);
    }

    @Transactional
    public StepLessonResponse generateLessonResponse(CurrentUser currentUser, UUID planId, int dayIndex,
                                                     int orderInDay, boolean regenerate) {
        LearningPlan plan = requireOwnedPlan(currentUser, planId);
        PlanStep step = findStep(plan, dayIndex, orderInDay);
        return generateLesson(currentUser.getUserId(), step, regenerate);
    }

    private LearningPlan requireOwnedPlan(CurrentUser currentUser, UUID planId) {
        return learningPlanRepository.findByIdAndUserId(planId, currentUser.getUserId())
                .orElseThrow(() -> new PlanException(PlanException.Reason.PLAN_NOT_FOUND));
    }

    private StepLessonResponse generateLesson(UUID userId, PlanStep step, boolean regenerate) {
        if (!regenerate && hasLesson(step)) {
            return planMapper.toLesson(step);
        }
        if (!lessonGenerator.isAvailable()) {
            if (hasLesson(step)) {
                return planMapper.toLesson(step);
            }
            throw new PlanException(PlanException.Reason.PLAN_LESSON_UNAVAILABLE);
        }
        if (!quotaService.canSpend(userId, LESSON_ESTIMATE_ILM_TOKENS)) {
            if (hasLesson(step)) {
                return planMapper.toLesson(step);
            }
            throw new PlanException(PlanException.Reason.PLAN_LESSON_QUOTA_EXCEEDED);
        }

        IlmTokenReservation reservation = quotaService.reserve(userId, LESSON_ESTIMATE_ILM_TOKENS);
        boolean committed = false;
        try {
            LessonDraft draft = lessonGenerator.generate(
                    userId, step.getTitle(), step.getNote(), step.getMaterialIds(), languageFor(userId));
            if (draft == null) {
                if (hasLesson(step)) {
                    return planMapper.toLesson(step);
                }
                throw new PlanException(PlanException.Reason.PLAN_LESSON_UNAVAILABLE);
            }
            quotaService.commit(reservation, LESSON_ESTIMATE_ILM_TOKENS);
            committed = true;
            step.setLessonContent(draft.getContent());
            step.setLessonCitations(draft.getCitations());
            step.setLessonGeneratedAt(OffsetDateTime.now(clock));
            return planMapper.toLesson(step);
        } finally {
            if (!committed) {
                quotaService.refund(reservation);
            }
        }
    }

    @Transactional
    public void markReplanNeeded(UUID userId) {
        learningPlanRepository.findFirstByUserIdAndStatusOrderByCreatedAtDesc(userId, PlanStatus.ACTIVE)
                .ifPresent(plan -> plan.setReplanNeeded(true));
    }

    @Transactional
    public boolean flagIfBehind(UUID userId, int thresholdDays) {
        if (userId == null) {
            return false;
        }
        int threshold = Math.max(1, thresholdDays);
        Optional<LearningPlan> active =
                learningPlanRepository.findFirstByUserIdAndStatusOrderByCreatedAtDesc(userId, PlanStatus.ACTIVE);
        if (active.isEmpty()) {
            return false;
        }
        LearningPlan plan = active.get();
        LocalDate today = LocalDate.ofInstant(clock.instant(), zoneFor(userId));
        long overdueSteps = plan.getSteps().stream()
                .filter(step -> !step.isDone())
                .map(PlanStep::getScheduledDate)
                .filter(Objects::nonNull)
                .filter(date -> date.isBefore(today))
                .count();
        if (overdueSteps >= threshold) {
            plan.setReplanNeeded(true);
            return true;
        }
        return false;
    }

    private static boolean hasLesson(PlanStep step) {
        return step.getLessonContent() != null && !step.getLessonContent().isBlank();
    }

    private String languageFor(UUID userId) {
        return profilesApi.find(userId)
                .map(ProfileDto::getLocale)
                .map(locale -> locale.getLocale().getLanguage())
                .orElse(null);
    }

    private void markStepDone(LearningPlan plan, int dayIndex) {
        OffsetDateTime now = OffsetDateTime.now();
        for (PlanStep step : plan.getSteps()) {
            if (step.getDayIndex() == dayIndex && !step.isDone()) {
                step.setDone(true);
                step.setCompletedAt(now);
            }
        }
    }

    private PlanStep toStep(LearningPlan plan, PlanStepInput input, int orderInDay) {
        PlanStep step = new PlanStep();
        step.setPlan(plan);
        step.setDayIndex(input.getDayIndex());
        step.setOrderInDay(orderInDay);
        step.setScheduledDate(input.getScheduledDate());
        step.setTitle(input.getTitle());
        step.setActivity(input.getActivity() == null ? PlanActivity.READ : input.getActivity());
        step.setMaterialIds(input.getMaterialIds());
        step.setNote(input.getNote());
        step.setDone(false);
        return step;
    }

    private ZoneId zoneFor(UUID userId) {
        return profilesApi.find(userId)
                .map(ProfileDto::getTimezone)
                .filter(tz -> tz != null && !tz.isBlank())
                .map(PlanService::parseZone)
                .orElse(DEFAULT_ZONE);
    }

    private static ZoneId parseZone(String tz) {
        try {
            return ZoneId.of(tz);
        } catch (RuntimeException ex) {
            return DEFAULT_ZONE;
        }
    }
}
