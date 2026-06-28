package org.aiincubator.ilmai.agent.service;

import org.aiincubator.ilmai.agent.RetrievedChunk;
import org.aiincubator.ilmai.ai.RetrievalApi;
import org.aiincubator.ilmai.ai.RetrievedChunkDto;
import org.aiincubator.ilmai.common.CurrentUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RetrieveToolUserIsolationTest {

    private final UUID userA = UUID.randomUUID();
    private final UUID userB = UUID.randomUUID();

    @AfterEach
    void tearDown() {
        AgentRetrievalContext.clear();
    }

    @Test
    void retrieveQueriesRetrievalApiWithIdFromToolContext_notFromToolArguments() {
        Map<UUID, List<RetrievedChunkDto>> perUser = new HashMap<>();
        UUID materialA = UUID.randomUUID();
        UUID materialB = UUID.randomUUID();
        perUser.put(userA, List.of(new RetrievedChunkDto(materialA, "A's notes", 0, "secret of A", 0.91)));
        perUser.put(userB, List.of(new RetrievedChunkDto(materialB, "B's notes", 0, "secret of B", 0.88)));

        RetrievalApi retrievalApi = (userId, roomId, query) -> perUser.getOrDefault(userId, List.of());
        RetrieveTool tool = new RetrieveTool(retrievalApi);

        AgentRetrievalContext.begin();
        ToolContext toolContextA = new ToolContext(Map.of(AgentToolContext.CURRENT_USER_KEY, new CurrentUser(userA)));
        List<RetrievedChunk> resultForA = tool.retrieve("anything", toolContextA);

        assertThat(resultForA).hasSize(1);
        assertThat(resultForA.get(0).getMaterialId()).isEqualTo(materialA);
        assertThat(resultForA.get(0).getSnippet()).isEqualTo("secret of A");

        AgentRetrievalContext.clear();

        AgentRetrievalContext.begin();
        ToolContext toolContextB = new ToolContext(Map.of(AgentToolContext.CURRENT_USER_KEY, new CurrentUser(userB)));
        List<RetrievedChunk> resultForB = tool.retrieve("anything", toolContextB);

        assertThat(resultForB).hasSize(1);
        assertThat(resultForB.get(0).getMaterialId()).isEqualTo(materialB);
        assertThat(resultForB.get(0).getSnippet()).isEqualTo("secret of B");
    }

    @Test
    void retrieveFailsWhenToolContextIsMissing() {
        RetrievalApi retrievalApi = (userId, roomId, query) -> List.of();
        RetrieveTool tool = new RetrieveTool(retrievalApi);

        assertThatThrownBy(() -> tool.retrieve("question", null))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void retrieveRecordsCallIntoAgentRetrievalContext() {
        UUID materialId = UUID.randomUUID();
        RetrievalApi retrievalApi = (userId, roomId, query) -> List.of(
                new RetrievedChunkDto(materialId, "Notes", 3, "snippet", 0.7));
        RetrieveTool tool = new RetrieveTool(retrievalApi);

        AgentRetrievalContext ctx = AgentRetrievalContext.begin();
        ToolContext toolContext = new ToolContext(Map.of(AgentToolContext.CURRENT_USER_KEY, new CurrentUser(userA)));

        tool.retrieve("anything", toolContext);

        assertThat(ctx.callCount()).isEqualTo(1);
        assertThat(ctx.hasGrounding()).isTrue();
        assertThat(ctx.chunks()).hasSize(1);
        assertThat(ctx.chunks().get(0).getMaterialId()).isEqualTo(materialId);
    }

    @Test
    void retrieveStopsQueryingAfterPerTurnCapReached() {
        UUID materialId = UUID.randomUUID();
        int[] calls = {0};
        RetrievalApi retrievalApi = (userId, roomId, query) -> {
            calls[0]++;
            return List.of(new RetrievedChunkDto(materialId, "Notes", 0, "snippet", 0.7));
        };
        RetrieveTool tool = new RetrieveTool(retrievalApi);

        AgentRetrievalContext.begin();
        ToolContext toolContext = new ToolContext(Map.of(AgentToolContext.CURRENT_USER_KEY, new CurrentUser(userA)));

        for (int i = 0; i < RetrieveTool.MAX_CALLS_PER_TURN; i++) {
            assertThat(tool.retrieve("query " + i, toolContext)).hasSize(1);
        }

        assertThat(tool.retrieve("one too many", toolContext)).isEmpty();
        assertThat(calls[0]).isEqualTo(RetrieveTool.MAX_CALLS_PER_TURN);
    }

    @Test
    void emptyQueryShortCircuitsToEmptyResult() {
        RetrievalApi retrievalApi = (userId, roomId, query) -> {
            throw new AssertionError("RetrievalApi must not be called for blank query");
        };
        RetrieveTool tool = new RetrieveTool(retrievalApi);

        AgentRetrievalContext.begin();
        ToolContext toolContext = new ToolContext(Map.of(AgentToolContext.CURRENT_USER_KEY, new CurrentUser(userA)));

        assertThat(tool.retrieve("   ", toolContext)).isEmpty();
        assertThat(tool.retrieve(null, toolContext)).isEmpty();
    }
}
