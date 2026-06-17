package org.aiincubator.ilmai.agent.service;

import lombok.extern.slf4j.Slf4j;
import org.aiincubator.ilmai.ai.IlmaiChatClientFactory;
import org.aiincubator.ilmai.gaps.GapItemDto;
import org.aiincubator.ilmai.gaps.GapsReportDto;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
@Slf4j
public class GapNarrator {

    private final ObjectProvider<ChatClient> gapsNarratorChatClientProvider;
    private final IlmTokenCostCalculator costCalculator;
    private final IlmaiChatClientFactory chatClientFactory;

    public GapNarrator(
            @Qualifier(CoachChatClientConfig.GAPS_NARRATOR_CHAT_CLIENT)
            ObjectProvider<ChatClient> gapsNarratorChatClientProvider,
            IlmTokenCostCalculator costCalculator,
            ObjectProvider<IlmaiChatClientFactory> chatClientFactoryProvider) {
        this.gapsNarratorChatClientProvider = gapsNarratorChatClientProvider;
        this.costCalculator = costCalculator;
        this.chatClientFactory = chatClientFactoryProvider.getIfAvailable();
    }

    public boolean isAvailable() {
        return gapsNarratorChatClientProvider.getIfAvailable() != null;
    }

    public GapNarrationDraft narrate(GapsReportDto report, String language) {
        ChatClient client = gapsNarratorChatClientProvider.getIfAvailable();
        if (client == null || report == null) {
            return null;
        }
        String userMessage = renderReport(report, language);
        ChatResponse response = client.prompt().user(userMessage).call().chatResponse();
        String narration = extractText(response).trim();
        if (narration.isEmpty()) {
            return null;
        }
        return new GapNarrationDraft(narration, computeCost(response));
    }

    private String renderReport(GapsReportDto report, String language) {
        StringBuilder sb = new StringBuilder();
        String lang = language == null ? "" : language.trim();
        if (!lang.isEmpty()) {
            sb.append("Language: ").append(lang).append('\n');
        }
        sb.append("Overall accuracy: ").append(Math.round(report.getOverallAccuracy() * 100)).append("% (")
                .append(report.getCorrectCount()).append('/').append(report.getTotalQuestionsAnswered())
                .append(" correct).\n");
        sb.append("Weak concepts:\n");
        if (report.getGaps().isEmpty()) {
            sb.append("- none\n");
        } else {
            for (GapItemDto gap : report.getGaps()) {
                appendConcept(sb, gap);
            }
        }
        sb.append("Strong concepts:\n");
        if (report.getStrengths().isEmpty()) {
            sb.append("- none\n");
        } else {
            for (GapItemDto strength : report.getStrengths()) {
                appendConcept(sb, strength);
            }
        }
        if (report.getRecommendedNext() != null && !report.getRecommendedNext().isBlank()) {
            sb.append("Recommended focus next: ").append(report.getRecommendedNext()).append('\n');
        }
        return sb.toString();
    }

    private void appendConcept(StringBuilder sb, GapItemDto item) {
        sb.append("- ").append(item.getConcept()).append(": ")
                .append(item.getHitCount()).append(" correct, ")
                .append(item.getMissCount()).append(" wrong (")
                .append(Math.round(item.getAccuracy() * 100)).append("% accuracy");
        if (item.getTrend() != null) {
            sb.append(", trend ").append(item.getTrend().name().toLowerCase(Locale.ROOT));
        }
        sb.append(")\n");
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
