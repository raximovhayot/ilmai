package org.aiincubator.ilmai.agent.service;

import org.aiincubator.ilmai.agent.ActionPart;
import org.aiincubator.ilmai.common.CurrentUser;
import org.aiincubator.ilmai.common.i18n.MessageService;
import org.aiincubator.ilmai.gaps.GapsApi;
import org.aiincubator.ilmai.gaps.GapsReportDto;
import org.aiincubator.ilmai.materials.MaterialDto;
import org.aiincubator.ilmai.materials.MaterialStatus;
import org.aiincubator.ilmai.materials.MaterialsApi;
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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ImproviseToolTest {

    private final UUID userId = UUID.randomUUID();

    private GapsApi gapsApi;
    private MaterialsApi materialsApi;
    private MessageService messageService;
    private UserMemoryApi userMemoryApi;
    private ImproviseTool tool;

    @BeforeEach
    void setUp() {
        gapsApi = mock(GapsApi.class);
        materialsApi = mock(MaterialsApi.class);
        messageService = mock(MessageService.class);
        when(messageService.get(any(), any(), any())).thenReturn("LABEL");
        userMemoryApi = mock(UserMemoryApi.class);
        when(userMemoryApi.dueReviews(any(), any())).thenReturn(List.of());
        tool = new ImproviseTool(gapsApi, materialsApi, messageService, userMemoryApi);
        AgentActionContext.begin();
    }

    @AfterEach
    void tearDown() {
        AgentActionContext.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void suggestsReviewWhenRecommendedNextPresent() {
        authenticate(userId);
        when(gapsApi.refreshAndGet(any())).thenReturn(Optional.of(report("photosynthesis")));

        ImprovisedTaskView view = tool.suggestStudyToday("en", new ToolContext(Map.of(AgentToolContext.CURRENT_USER_KEY, new CurrentUser(userId))));

        assertThat(view.isHasSuggestion()).isTrue();
        assertThat(view.getKind()).isEqualTo("review");
        assertThat(view.getConcept()).isEqualTo("photosynthesis");
        assertThat(recordedAction().getAction()).isEqualTo("review_concept");
        verifyNoInteractions(materialsApi);
    }

    @Test
    void prefersDueReviewConceptBeforeConsultingGaps() {
        authenticate(userId);
        when(userMemoryApi.dueReviews(any(), any()))
                .thenReturn(List.of(dueReview("mitosis"), dueReview("osmosis")));

        ImprovisedTaskView view = tool.suggestStudyToday("en", new ToolContext(Map.of(AgentToolContext.CURRENT_USER_KEY, new CurrentUser(userId))));

        assertThat(view.isHasSuggestion()).isTrue();
        assertThat(view.getKind()).isEqualTo("review");
        assertThat(view.getConcept()).isEqualTo("mitosis");
        assertThat(recordedAction().getAction()).isEqualTo("review_concept");
        verifyNoInteractions(gapsApi);
        verifyNoInteractions(materialsApi);
    }

    @Test
    void suggestsQuizWhenReportPresentButNoRecommendedNext() {
        authenticate(userId);
        when(gapsApi.refreshAndGet(any())).thenReturn(Optional.of(report(null)));
        when(materialsApi.findReadyForUser(userId)).thenReturn(List.of(material("Algebra")));

        ImprovisedTaskView view = tool.suggestStudyToday("ru", new ToolContext(Map.of(AgentToolContext.CURRENT_USER_KEY, new CurrentUser(userId))));

        assertThat(view.getKind()).isEqualTo("quiz");
        assertThat(view.getQuestionCount()).isEqualTo(ImproviseTool.DEFAULT_QUIZ_QUESTIONS);
        assertThat(recordedAction().getAction()).isEqualTo("start_quiz");
    }

    @Test
    void suggestsReadWhenNoReportButMaterials() {
        authenticate(userId);
        when(gapsApi.refreshAndGet(any())).thenReturn(Optional.empty());
        when(materialsApi.findReadyForUser(userId)).thenReturn(List.of(material("Algebra")));

        ImprovisedTaskView view = tool.suggestStudyToday("uz", new ToolContext(Map.of(AgentToolContext.CURRENT_USER_KEY, new CurrentUser(userId))));

        assertThat(view.getKind()).isEqualTo("read");
        assertThat(view.getMaterialTitle()).isEqualTo("Algebra");
        ActionPart action = recordedAction();
        assertThat(action.getAction()).isEqualTo("read_material");
        assertThat(action.getPayload()).containsEntry("materialTitle", "Algebra");
    }

    @Test
    void suggestsUploadWhenNoReportAndNoMaterials() {
        authenticate(userId);
        when(gapsApi.refreshAndGet(any())).thenReturn(Optional.empty());
        when(materialsApi.findReadyForUser(userId)).thenReturn(List.of());

        ImprovisedTaskView view = tool.suggestStudyToday("en", new ToolContext(Map.of(AgentToolContext.CURRENT_USER_KEY, new CurrentUser(userId))));

        assertThat(view.isHasSuggestion()).isFalse();
        assertThat(view.getKind()).isEqualTo("upload");
        assertThat(recordedAction().getAction()).isEqualTo("upload_material");
    }

    @Test
    void resolvesUserFromSecurityContextForBothLookups() {
        authenticate(userId);
        when(gapsApi.refreshAndGet(any())).thenReturn(Optional.empty());
        when(materialsApi.findReadyForUser(userId)).thenReturn(List.of());

        tool.suggestStudyToday("en", new ToolContext(Map.of(AgentToolContext.CURRENT_USER_KEY, new CurrentUser(userId))));

        ArgumentCaptor<CurrentUser> captor = ArgumentCaptor.forClass(CurrentUser.class);
        verify(gapsApi).refreshAndGet(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(userId);
        verify(materialsApi).findReadyForUser(userId);
    }

    @Test
    void failsWhenSecurityContextIsAnonymous() {
        assertThatThrownBy(() -> tool.suggestStudyToday("en", null))
                .isInstanceOf(IllegalStateException.class);
    }

    private ActionPart recordedAction() {
        List<ActionPart> actions = AgentActionContext.current().actions();
        assertThat(actions).hasSize(1);
        return actions.get(0);
    }

    private GapsReportDto report(String recommendedNext) {
        return new GapsReportDto(OffsetDateTime.now(), 4, 3, 0.75, "summary",
                List.of(), List.of(), recommendedNext);
    }

    private ReviewDueDto dueReview(String concept) {
        return new ReviewDueDto(concept, OffsetDateTime.now().minusDays(1), null, 1);
    }

    private MaterialDto material(String title) {
        return new MaterialDto(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), title,
                "application/pdf", 1024L, MaterialStatus.READY, 0, OffsetDateTime.now(), OffsetDateTime.now());
    }

    private void authenticate(UUID id) {
        CurrentUser principal = new CurrentUser(id);
        TestingAuthenticationToken auth = new TestingAuthenticationToken(principal, null);
        auth.setAuthenticated(true);
        SecurityContextHolder.getContext().setAuthentication(auth);
    }
}
