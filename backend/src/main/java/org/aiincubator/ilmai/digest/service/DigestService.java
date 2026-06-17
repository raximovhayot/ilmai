package org.aiincubator.ilmai.digest.service;

import lombok.extern.slf4j.Slf4j;
import org.aiincubator.ilmai.agent.DigestNarrationApi;
import org.aiincubator.ilmai.agent.DigestNarrationInput;
import org.aiincubator.ilmai.digest.DigestVariant;
import org.aiincubator.ilmai.digest.domain.WeeklyDigest;
import org.aiincubator.ilmai.digest.domain.WeeklyDigestRepository;
import org.aiincubator.ilmai.gaps.GapItemDto;
import org.aiincubator.ilmai.gaps.GapsApi;
import org.aiincubator.ilmai.gaps.GapsReportDto;
import org.aiincubator.ilmai.notifications.NotificationsApi;
import org.aiincubator.ilmai.notifications.OutboxChannel;
import org.aiincubator.ilmai.notifications.OutboxMessageRequest;
import org.aiincubator.ilmai.notifications.OutboxMessageType;
import org.aiincubator.ilmai.plan.LearningPlanDto;
import org.aiincubator.ilmai.plan.PlanApi;
import org.aiincubator.ilmai.plan.PlanStepDto;
import org.aiincubator.ilmai.profiles.ProfileDto;
import org.aiincubator.ilmai.profiles.ProfilesApi;
import org.aiincubator.ilmai.quiz.QuizApi;
import org.aiincubator.ilmai.quiz.WeeklyQuizStats;
import org.aiincubator.ilmai.streaks.StreaksApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.time.temporal.IsoFields;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Slf4j
@Service
public class DigestService {

    static final ZoneId DEFAULT_ZONE = ZoneId.of("Asia/Tashkent");
    private static final int MAX_TOP_GAPS = 3;
    private static final int NEW_USER_ACTIVITY_DAYS = 7;

    private final ProfilesApi profilesApi;
    private final QuizApi quizApi;
    private final StreaksApi streaksApi;
    private final GapsApi gapsApi;
    private final PlanApi planApi;
    private final DigestNarrationApi digestNarrationApi;
    private final NotificationsApi notificationsApi;
    private final DigestComposer composer;
    private final WeeklyDigestRepository weeklyDigests;
    private final Clock clock;
    private final int sendHour;

    public DigestService(ProfilesApi profilesApi,
                         QuizApi quizApi,
                         StreaksApi streaksApi,
                         GapsApi gapsApi,
                         PlanApi planApi,
                         DigestNarrationApi digestNarrationApi,
                         NotificationsApi notificationsApi,
                         DigestComposer composer,
                         WeeklyDigestRepository weeklyDigests,
                         Clock clock,
                         @Value("${digest.send-hour:19}") int sendHour) {
        this.profilesApi = profilesApi;
        this.quizApi = quizApi;
        this.streaksApi = streaksApi;
        this.gapsApi = gapsApi;
        this.planApi = planApi;
        this.digestNarrationApi = digestNarrationApi;
        this.notificationsApi = notificationsApi;
        this.composer = composer;
        this.weeklyDigests = weeklyDigests;
        this.clock = clock;
        this.sendHour = sendHour;
    }

    public int generateDueDigests() {
        int generated = 0;
        for (UUID userId : profilesApi.findAllUserIds()) {
            try {
                if (generateIfDue(userId)) {
                    generated++;
                }
            } catch (RuntimeException ex) {
                log.warn("weekly digest generation failed user={}", userId, ex);
            }
        }
        return generated;
    }

    public boolean generateIfDue(UUID userId) {
        ProfileDto profile = profilesApi.find(userId).orElse(null);
        ZoneId zone = zoneOf(profile);
        ZonedDateTime localNow = OffsetDateTime.now(clock).atZoneSameInstant(zone);
        if (localNow.getDayOfWeek() != DayOfWeek.SUNDAY) {
            return false;
        }
        int hour = profile != null && profile.getDailyReminder() != null
                ? profile.getDailyReminder().getHour()
                : sendHour;
        if (localNow.getHour() != hour) {
            return false;
        }
        return generate(userId, profile, localNow.toLocalDate(), zone) != null;
    }

    WeeklyDigest generate(UUID userId, ProfileDto profile, LocalDate localToday, ZoneId zone) {
        String isoWeek = isoWeek(localToday);
        if (weeklyDigests.existsByUserIdAndIsoWeek(userId, isoWeek)) {
            return null;
        }
        LocalDate weekStart = localToday.with(DayOfWeek.MONDAY);
        OffsetDateTime weekStartInstant = weekStart.atStartOfDay(zone).toOffsetDateTime();

        WeeklyQuizStats quiz = quizApi.weeklyStats(userId, weekStartInstant);
        int totalActiveDays = streaksApi.countActivityDays(userId);
        int weekActiveDays = streaksApi.countActivityDaysSince(userId, weekStart);
        int streakNow = streaksApi.getStreak(userId).getStreakCurrent();
        List<String> topGaps = topGaps(userId);
        int[] plan = planTotals(userId);
        Integer avgScore = quiz.getAnswered() > 0
                ? Math.round((quiz.getCorrect() * 100f) / quiz.getAnswered())
                : null;
        Integer daysUntilDeadline = profile != null && profile.getTargetDate() != null
                ? (int) ChronoUnit.DAYS.between(localToday, profile.getTargetDate())
                : null;

        WeeklyDigest digest = new WeeklyDigest();
        digest.setUserId(userId);
        digest.setIsoWeek(isoWeek);
        digest.setVariant(decideVariant(totalActiveDays, weekActiveDays));
        digest.setGeneratedAt(OffsetDateTime.now(clock));
        digest.setActiveDays(weekActiveDays);
        digest.setQuizzes(quiz.getQuizzes());
        digest.setAnswered(quiz.getAnswered());
        digest.setCorrect(quiz.getCorrect());
        digest.setAvgScore(avgScore);
        digest.setPlanDone(plan[0]);
        digest.setPlanTotal(plan[1]);
        digest.setStreakNow(streakNow);
        digest.setDaysUntilDeadline(daysUntilDeadline);
        digest.setTopGaps(topGaps);
        digest.setWhereYouStand(null);
        digest.setFocusNextWeek(List.of());

        if (digest.getVariant() == DigestVariant.FULL) {
            narrate(userId, profile, digest, topGaps);
        }

        WeeklyDigest saved = weeklyDigests.save(digest);

        notificationsApi.enqueue(OutboxMessageRequest.builder()
                .userId(userId)
                .channel(OutboxChannel.TELEGRAM)
                .type(OutboxMessageType.WEEKLY_DIGEST)
                .body(composer.compose(saved, profile))
                .dedupeKey("digest:" + userId + ":" + isoWeek)
                .scheduledFor(OffsetDateTime.now(clock))
                .build());

        log.debug("generated weekly digest user={} week={} variant={}", userId, isoWeek, saved.getVariant());
        return saved;
    }

    private void narrate(UUID userId, ProfileDto profile, WeeklyDigest digest, List<String> topGaps) {
        DigestNarrationInput input = DigestNarrationInput.builder()
                .language(languageOf(profile))
                .goal(profile == null ? null : profile.getGoal())
                .daysUntilDeadline(digest.getDaysUntilDeadline())
                .activeDays(digest.getActiveDays())
                .quizzes(digest.getQuizzes())
                .answered(digest.getAnswered())
                .correct(digest.getCorrect())
                .avgScorePercent(digest.getAvgScore())
                .planDone(digest.getPlanDone())
                .planTotal(digest.getPlanTotal())
                .streakNow(digest.getStreakNow())
                .topGaps(topGaps)
                .build();
        digestNarrationApi.narrate(userId, input).ifPresent(narration -> {
            digest.setWhereYouStand(narration.getWhereYouStand());
            digest.setFocusNextWeek(narration.getFocusNextWeek() == null
                    ? List.of() : narration.getFocusNextWeek());
        });
    }

    private DigestVariant decideVariant(int totalActiveDays, int weekActiveDays) {
        if (totalActiveDays < NEW_USER_ACTIVITY_DAYS) {
            return DigestVariant.NEW_USER;
        }
        if (weekActiveDays == 0) {
            return DigestVariant.INACTIVE;
        }
        return DigestVariant.FULL;
    }

    private List<String> topGaps(UUID userId) {
        GapsReportDto report = gapsApi.get(userId).orElse(null);
        if (report == null || report.getGaps() == null) {
            return List.of();
        }
        List<String> concepts = new ArrayList<>();
        for (GapItemDto gap : report.getGaps()) {
            if (concepts.size() >= MAX_TOP_GAPS) {
                break;
            }
            if (gap.getConcept() != null && !gap.getConcept().isBlank()) {
                concepts.add(gap.getConcept());
            }
        }
        return concepts;
    }

    private int[] planTotals(UUID userId) {
        return planApi.getActivePlanForUser(userId)
                .map(LearningPlanDto::getSteps)
                .map(steps -> new int[]{
                        (int) steps.stream().filter(PlanStepDto::isDone).count(),
                        steps.size()})
                .orElse(new int[]{0, 0});
    }

    private static String isoWeek(LocalDate date) {
        int week = date.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
        int weekYear = date.get(IsoFields.WEEK_BASED_YEAR);
        return String.format(Locale.ROOT, "%04d-W%02d", weekYear, week);
    }

    private static String languageOf(ProfileDto profile) {
        if (profile == null || profile.getLocale() == null) {
            return null;
        }
        return profile.getLocale().getLocale().getLanguage();
    }

    private static ZoneId zoneOf(ProfileDto profile) {
        if (profile == null || profile.getTimezone() == null || profile.getTimezone().isBlank()) {
            return DEFAULT_ZONE;
        }
        try {
            return ZoneId.of(profile.getTimezone());
        } catch (RuntimeException ex) {
            return DEFAULT_ZONE;
        }
    }
}
