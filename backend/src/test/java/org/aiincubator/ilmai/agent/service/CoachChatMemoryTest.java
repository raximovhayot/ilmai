package org.aiincubator.ilmai.agent.service;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CoachChatMemoryTest {

    private static ChatMemory memory() {
        return MessageWindowChatMemory.builder()
                .chatMemoryRepository(new InMemoryChatMemoryRepository())
                .maxMessages(CoachChatClientConfig.COACH_MEMORY_MAX_MESSAGES)
                .build();
    }

    @Test
    void recallsEarlierTurnsWithinTheSameSession() {
        ChatMemory memory = memory();
        String session = UUID.randomUUID().toString();

        memory.add(session, List.of(new UserMessage("My exam is IELTS on 2026-07-01")));
        memory.add(session, List.of(new AssistantMessage("Got it \u2014 IELTS on 2026-07-01.")));
        memory.add(session, List.of(new UserMessage("When is it again?")));

        List<Message> recalled = memory.get(session);

        assertThat(recalled).extracting(Message::getText)
                .anyMatch(text -> text.contains("IELTS on 2026-07-01"));
    }

    @Test
    void doesNotLeakMemoryAcrossSessions() {
        ChatMemory memory = memory();
        String sessionA = UUID.randomUUID().toString();
        String sessionB = UUID.randomUUID().toString();

        memory.add(sessionA, List.of(new UserMessage("secret-from-A")));

        assertThat(memory.get(sessionB)).isEmpty();
        assertThat(memory.get(sessionA)).extracting(Message::getText)
                .anyMatch(text -> text.contains("secret-from-A"));
    }
}
