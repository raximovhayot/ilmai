package org.aiincubator.ilmai.ai.config;

import org.aiincubator.ilmai.ai.IlmaiChatClientFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.ObjectProvider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class IlmaiChatClientFactoryTest {

    private ObjectProvider<ChatModel> chatModelProvider;
    private ChatModel geminiModel;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        chatModelProvider = mock(ObjectProvider.class);
        geminiModel = mock(ChatModel.class);
    }

    @Test
    void defaultProvider_isGemini() {
        IlmaiChatClientFactory factory = new IlmaiChatClientFactory(chatModelProvider);

        assertThat(factory.defaultProvider()).isEqualTo("GEMINI");
    }

    @Test
    void isAvailable_returnsFalseWhenNoChatModelBeansRegistered() {
        when(chatModelProvider.getIfAvailable()).thenReturn(null);

        IlmaiChatClientFactory factory = new IlmaiChatClientFactory(chatModelProvider);

        assertThat(factory.isAvailable()).isFalse();
        assertThat(factory.builder()).isNull();
    }

    @Test
    void builder_returnsBuilderWhenChatModelIsAvailable() {
        when(chatModelProvider.getIfAvailable()).thenReturn(geminiModel);

        IlmaiChatClientFactory factory = new IlmaiChatClientFactory(chatModelProvider);

        assertThat(factory.isAvailable()).isTrue();
        assertThat(factory.builder()).isNotNull();
    }
}
