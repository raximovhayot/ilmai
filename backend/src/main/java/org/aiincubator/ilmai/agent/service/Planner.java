package org.aiincubator.ilmai.agent.service;

import lombok.extern.slf4j.Slf4j;
import org.aiincubator.ilmai.ai.IlmaiChatClientFactory;
import org.aiincubator.ilmai.plan.PlanActivity;
import org.aiincubator.ilmai.plan.PlanStepInput;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@Slf4j
public class Planner {

    private static final Pattern JSON_ARRAY = Pattern.compile("\\[\\s*\\{.*}\\s*]", Pattern.DOTALL);

    private final ObjectProvider<ChatClient> plannerChatClientProvider;
    private final IlmTokenCostCalculator costCalculator;
    private final IlmaiChatClientFactory chatClientFactory;
    private final JsonMapper jsonMapper;

    public Planner(
            @Qualifier(CoachChatClientConfig.PLANNER_CHAT_CLIENT)
            ObjectProvider<ChatClient> plannerChatClientProvider,
            IlmTokenCostCalculator costCalculator,
            ObjectProvider<IlmaiChatClientFactory> chatClientFactoryProvider,
            JsonMapper jsonMapper) {
        this.plannerChatClientProvider = plannerChatClientProvider;
        this.costCalculator = costCalculator;
        this.chatClientFactory = chatClientFactoryProvider.getIfAvailable();
        this.jsonMapper = jsonMapper;
    }

    public boolean isAvailable() {
        return plannerChatClientProvider.getIfAvailable() != null;
    }

    public PlanDraft plan(PlannerBrief brief, List<PlannerMaterial> materials, LocalDate firstDay) {
        ChatClient client = plannerChatClientProvider.getIfAvailable();
        if (client == null || materials == null || materials.isEmpty()) {
            return null;
        }
        String userMessage = renderBrief(brief, materials);
        ChatResponse response = client.prompt().user(userMessage).call().chatResponse();
        List<PlanStepInput> steps = parseSteps(jsonMapper, extractText(response), materials, firstDay);
        if (steps.isEmpty()) {
            return null;
        }
        return new PlanDraft(steps, computeCost(response));
    }

    static List<PlanStepInput> parseSteps(JsonMapper jsonMapper, String response,
                                          List<PlannerMaterial> materials, LocalDate firstDay) {
        if (response == null || response.isBlank()) {
            return List.of();
        }
        Map<Integer, UUID> byNumber = new LinkedHashMap<>();
        for (PlannerMaterial material : materials) {
            byNumber.put(material.getNumber(), material.getId());
        }
        Matcher matcher = JSON_ARRAY.matcher(response);
        String json = matcher.find() ? matcher.group() : response.trim();
        List<Map<String, Object>> raw;
        try {
            raw = jsonMapper.readValue(json, new TypeReference<List<Map<String, Object>>>() {
            });
        } catch (RuntimeException ex) {
            log.warn("plan JSON parsing failed: {}", ex.toString());
            return List.of();
        }
        List<PlanStepInput> steps = new ArrayList<>(raw.size());
        for (Map<String, Object> item : raw) {
            String title = stringOrEmpty(item.get("title")).trim();
            if (title.isEmpty()) {
                continue;
            }
            int dayIndex = parseInt(item.get("day"), 1);
            if (dayIndex < 1) {
                dayIndex = 1;
            }
            LocalDate scheduledDate = firstDay == null ? null : firstDay.plusDays(dayIndex - 1L);
            PlanActivity activity = parseActivity(item.get("activity"));
            List<UUID> materialIds = resolveMaterials(item.get("materials"), byNumber);
            String note = stringOrEmpty(item.get("note")).trim();
            steps.add(new PlanStepInput(dayIndex, scheduledDate, title, activity,
                    materialIds, note.isEmpty() ? null : note));
        }
        return steps;
    }

    private static List<UUID> resolveMaterials(Object value, Map<Integer, UUID> byNumber) {
        List<UUID> ids = new ArrayList<>();
        if (value instanceof List<?> list) {
            for (Object element : list) {
                Integer number = asInt(element);
                if (number == null) {
                    continue;
                }
                UUID id = byNumber.get(number);
                if (id != null && !ids.contains(id)) {
                    ids.add(id);
                }
            }
        }
        return ids;
    }

    private String renderBrief(PlannerBrief brief, List<PlannerMaterial> materials) {
        StringBuilder sb = new StringBuilder();
        String language = brief.getLanguage() == null ? "" : brief.getLanguage().trim();
        if (!language.isEmpty()) {
            sb.append("Language: ").append(language).append('\n');
        }
        sb.append("Goal: ")
                .append(brief.getGoal() == null || brief.getGoal().isBlank() ? "general study" : brief.getGoal())
                .append('\n');
        if (brief.getTargetDate() != null) {
            sb.append("Deadline: ").append(brief.getTargetDate());
            if (brief.getDaysUntilDeadline() != null) {
                sb.append(" (").append(brief.getDaysUntilDeadline()).append(" days away)");
            }
            sb.append('\n');
        } else {
            sb.append("Deadline: none\n");
        }
        sb.append("Plan length: ").append(brief.getPlanDays()).append(" days\n");
        if (brief.getDailyStudyMinutes() != null) {
            sb.append("Daily study budget: ").append(brief.getDailyStudyMinutes()).append(" minutes\n");
        }
        sb.append("Materials (reference ONLY by number):\n");
        for (PlannerMaterial material : materials) {
            sb.append("  ").append(material.getNumber()).append(". ")
                    .append(material.getTitle() == null ? "Untitled" : material.getTitle()).append('\n');
        }
        sb.append("Weak concepts to prioritise: ");
        if (brief.getWeakConcepts() == null || brief.getWeakConcepts().isEmpty()) {
            sb.append("none yet\n");
        } else {
            sb.append(String.join(", ", brief.getWeakConcepts())).append('\n');
        }
        return sb.toString();
    }

    private String extractText(ChatResponse response) {
        if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
            return "";
        }
        String text = response.getResult().getOutput().getText();
        return text == null ? "" : text;
    }

    private int computeCost(ChatResponse response) {
        if (response == null) {
            return 0;
        }
        ChatResponseMetadata metadata = response.getMetadata();
        if (metadata == null) {
            return 0;
        }
        Usage usage = metadata.getUsage();
        long prompt = usage == null || usage.getPromptTokens() == null ? 0L : usage.getPromptTokens();
        long completion = usage == null || usage.getCompletionTokens() == null ? 0L : usage.getCompletionTokens();
        String provider = chatClientFactory != null ? chatClientFactory.defaultProvider() : null;
        String model = metadata.getModel();
        return costCalculator.costInIlmTokens(provider, model, prompt, completion);
    }

    private static PlanActivity parseActivity(Object value) {
        if (value == null) {
            return PlanActivity.READ;
        }
        try {
            return PlanActivity.valueOf(value.toString().trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return PlanActivity.READ;
        }
    }

    private static int parseInt(Object value, int fallback) {
        Integer parsed = asInt(value);
        return parsed == null ? fallback : parsed;
    }

    private static Integer asInt(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) {
            return null;
        }
        try {
            return (int) Math.round(Double.parseDouble(value.toString().trim()));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static String stringOrEmpty(Object value) {
        return value == null ? "" : value.toString();
    }
}
