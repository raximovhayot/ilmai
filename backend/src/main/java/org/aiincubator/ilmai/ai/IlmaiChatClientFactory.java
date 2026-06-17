package org.aiincubator.ilmai.ai;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@EnableConfigurationProperties(RetrievalProperties.class)
@Slf4j
public class IlmaiChatClientFactory {

    public static final String DEFAULT_PROVIDER = "GEMINI";

    private final ObjectProvider<ChatModel> chatModelProvider;

    public IlmaiChatClientFactory(ObjectProvider<ChatModel> chatModelProvider) {
        this.chatModelProvider = chatModelProvider;
    }

    public String defaultProvider() {
        return DEFAULT_PROVIDER;
    }

    public boolean isAvailable() {
        return chatModelProvider.getIfAvailable() != null;
    }

    public ChatClient.Builder builder() {
        ChatModel model = chatModelProvider.getIfAvailable();
        if (model == null) {
            return null;
        }
        return ChatClient.builder(model);
    }
}
