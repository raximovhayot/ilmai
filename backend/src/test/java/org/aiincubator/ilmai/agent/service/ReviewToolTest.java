package org.aiincubator.ilmai.agent.service;

import org.aiincubator.ilmai.common.CurrentUser;
import org.aiincubator.ilmai.agent.ReviewDueDto;
import org.aiincubator.ilmai.agent.UserMemoryApi;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReviewToolTest {

    private final UUID userId = UUID.randomUUID();

    private UserMemoryApi userMemoryApi;
    private ReviewTool tool;

    @BeforeEach
    void setUp() {
        userMemoryApi = mock(UserMemoryApi.class);
        tool = new ReviewTool(userMemoryApi);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void returnsDueConceptsResolvingUserFromSecurityContext() {
        authenticate(userId);
        when(userMemoryApi.dueReviews(any(), any()))
                .thenReturn(List.of(due("mitosis"), due("osmosis")));

        DueReviewsView view = tool.reviewDueConcepts(new ToolContext(Map.of(AgentToolContext.CURRENT_USER_KEY, new CurrentUser(userId))));

        assertThat(view.isHasDue()).isTrue();
        assertThat(view.getCount()).isEqualTo(2);
        assertThat(view.getConcepts()).containsExactly("mitosis", "osmosis");

        ArgumentCaptor<CurrentUser> captor = ArgumentCaptor.forClass(CurrentUser.class);
        verify(userMemoryApi).dueReviews(captor.capture(), any());
        assertThat(captor.getValue().getUserId()).isEqualTo(userId);
    }

    @Test
    void reportsNothingDueWhenQueueEmpty() {
        authenticate(userId);
        when(userMemoryApi.dueReviews(any(), any())).thenReturn(List.of());

        DueReviewsView view = tool.reviewDueConcepts(new ToolContext(Map.of(AgentToolContext.CURRENT_USER_KEY, new CurrentUser(userId))));

        assertThat(view.isHasDue()).isFalse();
        assertThat(view.getCount()).isZero();
        assertThat(view.getConcepts()).isEmpty();
    }

    @Test
    void capsListedConceptsButReportsHonestTotalCount() {
        authenticate(userId);
        List<ReviewDueDto> many = new ArrayList<>();
        for (int i = 0; i < ReviewTool.MAX_CONCEPTS + 3; i++) {
            many.add(due("concept-" + i));
        }
        when(userMemoryApi.dueReviews(any(), any())).thenReturn(many);

        DueReviewsView view = tool.reviewDueConcepts(new ToolContext(Map.of(AgentToolContext.CURRENT_USER_KEY, new CurrentUser(userId))));

        assertThat(view.isHasDue()).isTrue();
        assertThat(view.getCount()).isEqualTo(ReviewTool.MAX_CONCEPTS + 3);
        assertThat(view.getConcepts()).hasSize(ReviewTool.MAX_CONCEPTS);
        assertThat(view.getConcepts()).first().isEqualTo("concept-0");
    }

    @Test
    void failsWhenSecurityContextIsAnonymous() {
        assertThatThrownBy(() -> tool.reviewDueConcepts(null))
                .isInstanceOf(IllegalStateException.class);
    }

    private ReviewDueDto due(String concept) {
        return new ReviewDueDto(concept, OffsetDateTime.now().minusDays(1), null, 1);
    }

    private void authenticate(UUID id) {
        CurrentUser principal = new CurrentUser(id);
        TestingAuthenticationToken auth = new TestingAuthenticationToken(principal, null);
        auth.setAuthenticated(true);
        SecurityContextHolder.getContext().setAuthentication(auth);
    }
}
