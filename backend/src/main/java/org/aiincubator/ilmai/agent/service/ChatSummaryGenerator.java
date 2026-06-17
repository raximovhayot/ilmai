package org.aiincubator.ilmai.agent.service;

import lombok.extern.slf4j.Slf4j;
import org.aiincubator.ilmai.ai.IlmaiChatClientFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Slf4j
public class ChatSummaryGenerator {

    private final ObjectProvider<ChatClient> summaryChatClientProvider;
    private final IlmTokenCostCalculator costCalculator;
    private final IlmaiChatClientFactory chatClientFactory;

    public ChatSummaryGenerator(
            @Qualifier(CoachChatClientConfig.SUMMARY_CHAT_CLIENT) ObjectProvider<ChatClient> summaryChatClientProvider,
            IlmTokenCostCalculator costCalculator,
            ObjectProvider<IlmaiChatClientFactory> chatClientFactoryProvider) {
        this.summaryChatClientProvider = summaryChatClientProvider;
        this.costCalculator = costCalculator;
        this.chatClientFactory = chatClientFactoryProvider.getIfAvailable();
    }

    public boolean isAvailable() {
        return summaryChatClientProvider.getIfAvailable() != null;
    }

    public ChatMemorySummaryDraft summarize(String existingSummary, List<Message> turns) {
        ChatClient client = summaryChatClientProvider.getIfAvailable();
        if (client == null || turns == null || turns.isEmpty()) {
            return null;
        }
        String userMessage = renderTranscript(existingSummary, turns);
        if (userMessage.isBlank()) {
            return null;
        }
        ChatResponse response = client.prompt().user(userMessage).call().chatResponse();
        String summary = extractText(response).trim();
        if (summary.isEmpty()) {
            return null;
        }
        return new ChatMemorySummaryDraft(summary, computeCost(response));
    }

    private String renderTranscript(String existingSummary, List<Message> turns) {
        StringBuilder sb = new StringBuilder();
        if (existingSummary != null && !existingSummary.isBlank()) {
            sb.append("Prior summary:\n").append(existingSummary.trim()).append("\n\n");
        }
        sb.append("New conversation turns to fold in:\n");
        for (Message message : turns) {
            String text = message.getText();
            if (text == null || text.isBlank()) {
                continue;
            }
            sb.append(roleLabel(message)).append(": ").append(text.trim()).append('\n');
        }
        return sb.toString();
    }

    private String roleLabel(Message message) {
        if (message.getMessageType() == null) {
            return "Message";
        }
        return switch (message.getMessageType()) {
            case USER -> "Learner";
            case ASSISTANT -> "Coach";
            case TOOL -> "Tool";
            case SYSTEM -> "System";
        };
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
