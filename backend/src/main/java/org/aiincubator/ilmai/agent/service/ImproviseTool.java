package org.aiincubator.ilmai.agent.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aiincubator.ilmai.agent.ActionPart;
import org.aiincubator.ilmai.common.CurrentUser;
import org.aiincubator.ilmai.common.i18n.MessageService;
import org.aiincubator.ilmai.common.i18n.SupportedLocale;
import org.aiincubator.ilmai.gaps.GapsApi;
import org.aiincubator.ilmai.gaps.GapsReportDto;
import org.aiincubator.ilmai.materials.MaterialDto;
import org.aiincubator.ilmai.materials.MaterialsApi;
import org.aiincubator.ilmai.agent.ReviewDueDto;
import org.aiincubator.ilmai.agent.UserMemoryApi;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;
import uz.uzinfoweb.uimessagestream.spring.SerializedPartSink;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class ImproviseTool {

    static final int DEFAULT_QUIZ_QUESTIONS = 5;

    private final GapsApi gapsApi;
    private final MaterialsApi materialsApi;
    private final MessageService messageService;
    private final UserMemoryApi userMemoryApi;

    @Tool(description = "Suggest ONE concrete, lightweight study action for the current user right now - use this "
            + "when there is no scheduled plan step for today (getTodaysTask returned nothing) or the user asks "
            + "'what should I do now?'. This NEVER starts the action, it only proposes one: the returned suggestion "
            + "is surfaced to the user as a tappable button, so describe it in their language and let them decide. "
            + "When hasSuggestion is false the user has no materials yet - invite them to upload some first.")
    public ImprovisedTaskView suggestStudyToday(
            @ToolParam(required = false, description = "Language for the suggestion label: 'en', 'ru', or 'uz'. "
                    + "Use the language the user is writing in.")
            String language,
            ToolContext toolContext) {
        CurrentUser currentUser = AgentToolContext.requireCurrentUser(toolContext);
        Locale locale = resolveLocale(language);

        List<ReviewDueDto> due = userMemoryApi.dueReviews(currentUser, OffsetDateTime.now());
        if (!due.isEmpty()) {
            String dueConcept = due.get(0).getConcept();
            return suggest(true, "review", dueConcept, null, 0,
                    "agent.action.review.label", new Object[]{dueConcept},
                    Map.of("concept", dueConcept), locale, currentUser, toolContext);
        }

        GapsReportDto report = gapsApi.refreshAndGet(currentUser).orElse(null);
        String recommended = report == null ? null : report.getRecommendedNext();
        if (recommended != null && !recommended.isBlank()) {
            return suggest(true, "review", recommended, null, 0,
                    "agent.action.review.label", new Object[]{recommended},
                    Map.of("concept", recommended), locale, currentUser, toolContext);
        }

        List<MaterialDto> ready = materialsApi.findReadyForUser(currentUser.getUserId());
        if (!ready.isEmpty()) {
            if (report != null) {
                return suggest(true, "quiz", null, null, DEFAULT_QUIZ_QUESTIONS,
                        "agent.action.quiz.label", new Object[]{DEFAULT_QUIZ_QUESTIONS},
                        Map.of("questionCount", DEFAULT_QUIZ_QUESTIONS), locale, currentUser, toolContext);
            }
            MaterialDto material = ready.get(0);
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("materialId", material.getId().toString());
            payload.put("materialTitle", material.getTitle());
            return suggest(true, "read", null, material.getTitle(), 0,
                    "agent.action.read.label", new Object[]{material.getTitle()},
                    payload, locale, currentUser, toolContext);
        }

        return suggest(false, "upload", null, null, 0,
                "agent.action.upload.label", null,
                Map.of(), locale, currentUser, toolContext);
    }

    private ImprovisedTaskView suggest(boolean hasSuggestion, String kind, String concept, String materialTitle,
                                       int questionCount, String labelKey, Object[] labelArgs,
                                       Map<String, Object> payload, Locale locale, CurrentUser currentUser,
                                       ToolContext toolContext) {
        String label = messageService.get(labelKey, labelArgs, locale);
        AgentActionContext ctx = AgentActionContext.current();
        if (ctx != null) {
            ctx.record(new ActionPart(actionFor(kind), label, payload));
        }
        SerializedPartSink sink = AgentToolContext.sink(toolContext);
        if (sink != null) {
            sink.data("action", Map.of(
                    "action", actionFor(kind),
                    "label", label,
                    "payload", payload == null ? Map.of() : payload
            ));
        }
        log.debug("agent.suggestStudyToday user={} kind={}", currentUser.getUserId(), kind);
        return new ImprovisedTaskView(hasSuggestion, kind, concept, materialTitle, questionCount, label);
    }

    private static String actionFor(String kind) {
        return switch (kind) {
            case "review" -> "review_concept";
            case "quiz" -> "start_quiz";
            case "read" -> "read_material";
            default -> "upload_material";
        };
    }

    private Locale resolveLocale(String language) {
        return SupportedLocale.fromLanguageTag(language)
                .map(SupportedLocale::getLocale)
                .orElse(SupportedLocale.DEFAULT.getLocale());
    }
}
