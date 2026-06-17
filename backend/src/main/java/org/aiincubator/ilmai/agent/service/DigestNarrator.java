package org.aiincubator.ilmai.agent.service;

import lombok.extern.slf4j.Slf4j;
import org.aiincubator.ilmai.agent.DigestNarrationInput;
import org.aiincubator.ilmai.ai.IlmaiChatClientFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@Slf4j
public class DigestNarrator {

    private static final Pattern JSON_OBJECT = Pattern.compile("\\{.*}", Pattern.DOTALL);

    private final ObjectProvider<ChatClient> digestNarratorChatClientProvider;
    private final IlmTokenCostCalculator costCalculator;
    private final IlmaiChatClientFactory chatClientFactory;
    private final JsonMapper jsonMapper;

    public DigestNarrator(
            @Qualifier(CoachChatClientConfig.DIGEST_NARRATOR_CHAT_CLIENT)
            ObjectProvider<ChatClient> digestNarratorChatClientProvider,
            IlmTokenCostCalculator costCalculator,
            ObjectProvider<IlmaiChatClientFactory> chatClientFactoryProvider,
            JsonMapper jsonMapper) {
        this.digestNarratorChatClientProvider = digestNarratorChatClientProvider;
        this.costCalculator = costCalculator;
        this.chatClientFactory = chatClientFactoryProvider.getIfAvailable();
        this.jsonMapper = jsonMapper;
    }

    public boolean isAvailable() {
        return digestNarratorChatClientProvider.getIfAvailable() != null;
    }

    public DigestNarrationDraft narrate(DigestNarrationInput input) {
        ChatClient client = digestNarratorChatClientProvider.getIfAvailable();
        if (client == null || input == null) {
            return null;
        }
        String userMessage = renderBrief(input);
        ChatResponse response = client.prompt().user(userMessage).call().chatResponse();
        String text = extractText(response).trim();
        String whereYouStand = "";
        List<String> focusNextWeek = new ArrayList<>();
        Matcher matcher = JSON_OBJECT.matcher(text);
        String json = matcher.find() ? matcher.group() : text;
        try {
            Map<String, Object> raw = jsonMapper.readValue(json, new TypeReference<Map<String, Object>>() {
            });
            Object stand = raw.get("whereYouStand");
            if (stand != null) {
                whereYouStand = stand.toString().trim();
            }
            focusNextWeek = parseFocus(raw.get("focusNextWeek"));
        } catch (RuntimeException ex) {
            log.warn("digest narration JSON parsing failed: {}", ex.toString());
            whereYouStand = text;
        }
        if (whereYouStand.isEmpty() && focusNextWeek.isEmpty()) {
            return null;
        }
        return new DigestNarrationDraft(whereYouStand, focusNextWeek, computeCost(response));
    }

    private static List<String> parseFocus(Object value) {
        List<String> items = new ArrayList<>();
        if (value instanceof List<?> list) {
            for (Object element : list) {
                if (element == null) {
                    continue;
                }
                String item = element.toString().trim();
                if (!item.isEmpty()) {
                    items.add(item);
                }
            }
        } else if (value != null) {
            String item = value.toString().trim();
            if (!item.isEmpty()) {
                items.add(item);
            }
        }
        return items;
    }

    private String renderBrief(DigestNarrationInput input) {
        StringBuilder sb = new StringBuilder();
        String language = input.getLanguage() == null ? "" : input.getLanguage().trim();
        if (!language.isEmpty()) {
            sb.append("Language: ").append(language).append('\n');
        }
        sb.append("Goal: ")
                .append(input.getGoal() == null || input.getGoal().isBlank() ? "general study" : input.getGoal())
                .append('\n');
        if (input.getDaysUntilDeadline() != null) {
            sb.append("Days until deadline: ").append(input.getDaysUntilDeadline()).append('\n');
        }
        sb.append("Active days this week: ").append(input.getActiveDays()).append('\n');
        sb.append("Quizzes this week: ").append(input.getQuizzes()).append('\n');
        sb.append("Questions answered this week: ").append(input.getAnswered())
                .append(" (").append(input.getCorrect()).append(" correct");
        if (input.getAvgScorePercent() != null) {
            sb.append(", ").append(input.getAvgScorePercent()).append("% accuracy");
        }
        sb.append(")\n");
        sb.append("Plan progress: ").append(input.getPlanDone()).append('/').append(input.getPlanTotal())
                .append(" steps done\n");
        sb.append("Current streak: ").append(input.getStreakNow()).append(" days\n");
        sb.append("Weak concepts: ");
        if (input.getTopGaps() == null || input.getTopGaps().isEmpty()) {
            sb.append("none yet\n");
        } else {
            sb.append(String.join(", ", input.getTopGaps())).append('\n');
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
}
