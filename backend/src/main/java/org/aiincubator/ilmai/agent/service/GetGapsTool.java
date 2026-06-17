package org.aiincubator.ilmai.agent.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aiincubator.ilmai.common.CurrentUser;
import org.aiincubator.ilmai.common.quota.IlmTokenReservation;
import org.aiincubator.ilmai.common.quota.QuotaService;
import org.aiincubator.ilmai.gaps.GapItemDto;
import org.aiincubator.ilmai.gaps.GapsApi;
import org.aiincubator.ilmai.gaps.GapsReportDto;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Component
@RequiredArgsConstructor
@Slf4j
public class GetGapsTool {

    static final int GAPS_NARRATION_ESTIMATE_ILM_TOKENS = 3;

    private final GapsApi gapsApi;
    private final GapNarrator gapNarrator;
    private final QuotaService quotaService;

    @Tool(description = "Report what the current user is weak on and strong at, based on their own quiz performance. "
            + "Returns structured per-concept accuracy (gaps and strengths) plus a short narrated summary. Call this "
            + "when the user asks what they should review, what they are struggling with, or how they are doing. If "
            + "the user has not answered any quiz questions yet, ready is false - invite them to take a quiz first.")
    public GapsView getGaps(
            @ToolParam(required = false, description = "Language to narrate the report in: 'en', 'ru', or 'uz'. "
                    + "Use the language the user is writing in.")
            String language,
            ToolContext toolContext) {
        CurrentUser currentUser = AgentToolContext.requireCurrentUser(toolContext);
        GapsReportDto report = gapsApi.refreshAndGet(currentUser).orElse(null);
        if (report == null) {
            log.debug("agent.getGaps not-ready user={}", currentUser.getUserId());
            return new GapsView(false, null, 0.0, 0, 0, null, List.of(), List.of());
        }
        String narration = narrate(currentUser, report, language);
        return new GapsView(
                true,
                narration,
                report.getOverallAccuracy(),
                report.getTotalQuestionsAnswered(),
                report.getCorrectCount(),
                report.getRecommendedNext(),
                toConceptViews(report.getGaps()),
                toConceptViews(report.getStrengths()));
    }

    private String narrate(CurrentUser currentUser, GapsReportDto report, String language) {
        if (!gapNarrator.isAvailable()) {
            return null;
        }
        if (!quotaService.canSpend(currentUser.getUserId(), GAPS_NARRATION_ESTIMATE_ILM_TOKENS)) {
            log.debug("agent.getGaps narration skipped (quota) user={}", currentUser.getUserId());
            return null;
        }
        IlmTokenReservation reservation =
                quotaService.reserve(currentUser.getUserId(), GAPS_NARRATION_ESTIMATE_ILM_TOKENS);
        boolean committed = false;
        try {
            GapNarrationDraft draft = gapNarrator.narrate(report, language);
            if (draft == null) {
                return null;
            }
            quotaService.commit(reservation, draft.getIlmTokenCost());
            committed = true;
            return draft.getNarration();
        } finally {
            if (!committed) {
                quotaService.refund(reservation);
            }
        }
    }

    private List<GapConceptView> toConceptViews(List<GapItemDto> items) {
        List<GapConceptView> views = new ArrayList<>();
        for (GapItemDto item : items) {
            views.add(new GapConceptView(
                    item.getConcept(),
                    item.getAccuracy(),
                    item.getHitCount(),
                    item.getMissCount(),
                    item.getSuggestedMaterialName(),
                    item.getTrend() == null ? null : item.getTrend().name().toLowerCase(Locale.ROOT)));
        }
        return views;
    }
}
