package org.aiincubator.ilmai.agent.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aiincubator.ilmai.agent.RetrievedChunk;
import org.aiincubator.ilmai.ai.RetrievalApi;
import org.aiincubator.ilmai.ai.RetrievedChunkDto;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;
import uz.uzinfoweb.uimessagestream.spring.SerializedPartSink;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class RetrieveTool {

    private final RetrievalApi retrievalApi;

    @Tool(description = "Search the current user's uploaded materials (PDFs, documents, notes) for chunks "
            + "relevant to the query. Returns chunks with their materialId so they can be cited back as "
            + "[#<materialId>:<locator>]. Always call this before answering any factual question about the "
            + "user's materials. Returns an empty list if nothing matches — in that case do not answer from "
            + "world knowledge.")
    public List<RetrievedChunk> retrieve(
            @ToolParam(description = "Natural-language query in the user's language.") String query,
            ToolContext toolContext) {
        UUID userId = AgentToolContext.requireUserId(toolContext);
        List<RetrievedChunkDto> raw = query == null || query.isBlank()
                ? List.of()
                : retrievalApi.retrieve(userId, query);
        List<RetrievedChunk> chunks = raw.stream()
                .map(dto -> new RetrievedChunk(
                        dto.getMaterialId(),
                        dto.getMaterialName(),
                        dto.getChunkIndex(),
                        dto.getContent(),
                        dto.getScore()))
                .toList();
        AgentRetrievalContext threadLocalCtx = AgentRetrievalContext.current();
        if (threadLocalCtx != null) {
            threadLocalCtx.recordCall(chunks);
        }
        AgentRetrievalContext turnCtx = AgentToolContext.retrievalContext(toolContext);
        if (turnCtx != null && turnCtx != threadLocalCtx) {
            turnCtx.recordCall(chunks);
        }
        emitCitations(AgentToolContext.sink(toolContext), chunks);
        log.debug("agent.retrieve user={} query='{}' chunks={}", userId,
                query == null ? "" : query.replaceAll("\\s+", " "),
                chunks.size());
        return chunks;
    }

    private void emitCitations(SerializedPartSink sink, List<RetrievedChunk> chunks) {
        if (sink == null) {
            return;
        }
        for (RetrievedChunk chunk : chunks) {
            sink.data("citation", Map.of(
                    "id", UUID.randomUUID(),
                    "materialId", chunk.getMaterialId(),
                    "materialName", chunk.getMaterialName() == null ? "" : chunk.getMaterialName(),
                    "locator", chunk.getChunkIndex() == null ? "" : "t" + chunk.getChunkIndex(),
                    "snippet", chunk.getSnippet() == null ? "" : chunk.getSnippet(),
                    "score", chunk.getScore() == null ? 0.0 : chunk.getScore()
            ));
        }
    }
}
