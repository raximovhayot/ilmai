package org.aiincubator.ilmai.plan.service;

import lombok.extern.slf4j.Slf4j;
import org.aiincubator.ilmai.ai.IlmaiChatClientFactory;
import org.aiincubator.ilmai.ai.RetrievalApi;
import org.aiincubator.ilmai.ai.RetrievedChunkDto;
import org.aiincubator.ilmai.ai.SourceLocator;
import org.aiincubator.ilmai.plan.domain.LessonCitation;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Component
@Slf4j
public class PlanLessonGenerator {

    private static final int MAX_CHUNKS = 6;
    private static final int SNIPPET_MAX_CHARS = 320;

    private final ObjectProvider<IlmaiChatClientFactory> chatClientFactoryProvider;
    private final RetrievalApi retrievalApi;
    private final PlanLessonSystemPrompt systemPrompt;

    private volatile ChatClient chatClient;

    public PlanLessonGenerator(ObjectProvider<IlmaiChatClientFactory> chatClientFactoryProvider,
                               RetrievalApi retrievalApi,
                               PlanLessonSystemPrompt systemPrompt) {
        this.chatClientFactoryProvider = chatClientFactoryProvider;
        this.retrievalApi = retrievalApi;
        this.systemPrompt = systemPrompt;
    }

    public boolean isAvailable() {
        IlmaiChatClientFactory factory = chatClientFactoryProvider.getIfAvailable();
        return factory != null && factory.isAvailable();
    }

    public LessonDraft generate(UUID userId, String title, String note, List<UUID> materialIds, String language) {
        if (userId == null || title == null || title.isBlank()) {
            return null;
        }
        ChatClient client = client();
        if (client == null) {
            return null;
        }
        String query = note == null || note.isBlank() ? title : title + " - " + note;
        List<RetrievedChunkDto> chunks = selectChunks(retrievalApi.retrieve(userId, query), materialIds);
        if (chunks.isEmpty()) {
            return null;
        }
        String userMessage = renderUserMessage(title, note, language, chunks);
        ChatResponse response;
        try {
            response = client.prompt().user(userMessage).call().chatResponse();
        } catch (RuntimeException ex) {
            log.warn("plan lesson generation failed user={}: {}", userId, ex.toString());
            return null;
        }
        String content = extractText(response).trim();
        if (content.isEmpty()) {
            return null;
        }
        return new LessonDraft(content, toCitations(chunks));
    }

    private List<RetrievedChunkDto> selectChunks(List<RetrievedChunkDto> retrieved, List<UUID> materialIds) {
        if (retrieved == null || retrieved.isEmpty()) {
            return List.of();
        }
        List<RetrievedChunkDto> scoped = retrieved;
        if (materialIds != null && !materialIds.isEmpty()) {
            Set<UUID> allowed = new HashSet<>(materialIds);
            List<RetrievedChunkDto> filtered = retrieved.stream()
                    .filter(chunk -> chunk.getMaterialId() != null && allowed.contains(chunk.getMaterialId()))
                    .toList();
            if (!filtered.isEmpty()) {
                scoped = filtered;
            }
        }
        return scoped.size() > MAX_CHUNKS ? scoped.subList(0, MAX_CHUNKS) : scoped;
    }

    private String renderUserMessage(String title, String note, String language, List<RetrievedChunkDto> chunks) {
        StringBuilder sb = new StringBuilder();
        sb.append("Language: ").append(language == null || language.isBlank() ? "en" : language).append('\n');
        sb.append("Plan step: ").append(title).append('\n');
        if (note != null && !note.isBlank()) {
            sb.append("Note: ").append(note).append('\n');
        }
        sb.append("\nExcerpts from the learner's materials:\n");
        int i = 1;
        for (RetrievedChunkDto chunk : chunks) {
            String name = chunk.getMaterialName() == null ? "material" : chunk.getMaterialName();
            sb.append('[').append(i).append("] (").append(name).append(")\n");
            sb.append(truncate(chunk.getContent())).append("\n\n");
            i++;
        }
        return sb.toString().trim();
    }

    private List<LessonCitation> toCitations(List<RetrievedChunkDto> chunks) {
        List<LessonCitation> citations = new ArrayList<>(chunks.size());
        for (RetrievedChunkDto chunk : chunks) {
            LessonCitation citation = new LessonCitation(
                    chunk.getMaterialId(),
                    chunk.getMaterialName(),
                    chunk.getChunkIndex(),
                    truncate(chunk.getContent()));
            SourceLocator locator = chunk.getLocator();
            if (locator != null) {
                citation.setSourceKind(locator.getKind());
                citation.setPageStart(locator.getPageStart());
                citation.setPageEnd(locator.getPageEnd());
                citation.setAudioStartMs(locator.getAudioStartMs());
                citation.setAudioEndMs(locator.getAudioEndMs());
            }
            citations.add(citation);
        }
        return citations;
    }

    private static String truncate(String text) {
        if (text == null) {
            return "";
        }
        String trimmed = text.strip();
        if (trimmed.length() <= SNIPPET_MAX_CHARS) {
            return trimmed;
        }
        return trimmed.substring(0, SNIPPET_MAX_CHARS).strip() + "...";
    }

    private static String extractText(ChatResponse response) {
        if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
            return "";
        }
        String text = response.getResult().getOutput().getText();
        return text == null ? "" : text;
    }

    private ChatClient client() {
        ChatClient existing = chatClient;
        if (existing != null) {
            return existing;
        }
        IlmaiChatClientFactory factory = chatClientFactoryProvider.getIfAvailable();
        if (factory == null) {
            return null;
        }
        ChatClient.Builder builder = factory.builder();
        if (builder == null) {
            return null;
        }
        ChatClient built = builder.defaultSystem(systemPrompt.get()).build();
        chatClient = built;
        return built;
    }
}
