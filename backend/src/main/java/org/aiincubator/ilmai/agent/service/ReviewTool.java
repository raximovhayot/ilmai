package org.aiincubator.ilmai.agent.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aiincubator.ilmai.common.CurrentUser;
import org.aiincubator.ilmai.agent.ReviewDueDto;
import org.aiincubator.ilmai.agent.UserMemoryApi;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class ReviewTool {

    static final int MAX_CONCEPTS = 10;

    private final UserMemoryApi userMemoryApi;

    @Tool(description = "List the concepts the current user is due to review now under spaced repetition - concepts "
            + "they previously got wrong on a quiz whose scheduled review time has arrived. Call this when the user "
            + "asks what to review or how to revise, or proactively to prompt a quick review. Returns hasDue=false "
            + "when nothing is due yet. When concepts are due, offer to test the user on them by calling startQuiz "
            + "with a due concept as the scope.")
    public DueReviewsView reviewDueConcepts(ToolContext toolContext) {
        CurrentUser currentUser = AgentToolContext.requireCurrentUser(toolContext);
        List<ReviewDueDto> due = userMemoryApi.dueReviews(currentUser, OffsetDateTime.now());
        List<String> concepts = new ArrayList<>();
        for (ReviewDueDto entry : due) {
            if (concepts.size() >= MAX_CONCEPTS) {
                break;
            }
            concepts.add(entry.getConcept());
        }
        log.debug("agent.reviewDueConcepts user={} due={}", currentUser.getUserId(), due.size());
        return new DueReviewsView(!due.isEmpty(), due.size(), concepts);
    }
}
