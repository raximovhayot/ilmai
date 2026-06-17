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

import java.util.ArrayList;
import java.util.List;

@Component
@Slf4j
public class UserFactGenerator {

    static final int MAX_FACTS = 5;

    private final ObjectProvider<ChatClient> factsChatClientProvider;
    private final IlmTokenCostCalculator costCalculator;
    private final IlmaiChatClientFactory chatClientFactory;

    public UserFactGenerator(
            @Qualifier(CoachChatClientConfig.FACTS_CHAT_CLIENT) ObjectProvider<ChatClient> factsChatClientProvider,
            IlmTokenCostCalculator costCalculator,
            ObjectProvider<IlmaiChatClientFactory> chatClientFactoryProvider) {
        this.factsChatClientProvider = factsChatClientProvider;
        this.costCalculator = costCalculator;
        this.chatClientFactory = chatClientFactoryProvider.getIfAvailable();
    }

    public boolean isAvailable() {
        return factsChatClientProvider.getIfAvailable() != null;
    }

    public UserFactExtractionDraft extract(List<Message> turns) {
        ChatClient client = factsChatClientProvider.getIfAvailable();
        if (client == null || turns == null || turns.isEmpty()) {
            return null;
        }
        String userMessage = renderTranscript(turns);
        if (userMessage.isBlank()) {
            return null;
        }
        ChatResponse response = client.prompt().user(userMessage).call().chatResponse();
        List<String> facts = parseFacts(extractText(response));
        if (facts.isEmpty()) {
            return null;
        }
        return new UserFactExtractionDraft(facts, computeCost(response));
    }

    private String renderTranscript(List<Message> turns) {
        StringBuilder sb = new StringBuilder();
        sb.append("Recent conversation turns:\n");
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

    private List<String> parseFacts(String text) {
        List<String> facts = new ArrayList<>();
        if (text == null) {
            return facts;
        }
        for (String rawLine : text.split("\\R")) {
            String line = stripBullet(rawLine.trim());
            if (line.isEmpty() || line.equalsIgnoreCase("NONE")) {
                continue;
            }
            facts.add(line);
            if (facts.size() >= MAX_FACTS) {
                break;
            }
        }
        return facts;
    }

    private String stripBullet(String line) {
        String stripped = line;
        while (!stripped.isEmpty()
                && (stripped.charAt(0) == '-' || stripped.charAt(0) == '*' || stripped.charAt(0) == '\u2022')) {
            stripped = stripped.substring(1).stripLeading();
        }
        return stripped.trim();
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
