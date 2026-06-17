package org.aiincubator.ilmai.agent.service;

import org.aiincubator.ilmai.common.CurrentUser;
import org.aiincubator.ilmai.common.quota.QuotaService;
import org.aiincubator.ilmai.gaps.GapItemDto;
import org.aiincubator.ilmai.gaps.GapTrend;
import org.aiincubator.ilmai.gaps.GapsApi;
import org.aiincubator.ilmai.gaps.GapsReportDto;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class GetGapsToolUserIsolationTest {

    private final UUID userA = UUID.randomUUID();
    private final UUID userB = UUID.randomUUID();

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void getGapsResolvesUserFromSecurityContext_andReturnsOnlyThatUsersGaps() {
        AtomicReference<CurrentUser> captured = new AtomicReference<>();
        Map<UUID, GapsReportDto> byUser = new HashMap<>();
        byUser.put(userA, reportWithGap("photosynthesis"));
        byUser.put(userB, reportWithGap("derivatives"));
        CapturingGapsApi gapsApi = new CapturingGapsApi(captured, byUser);
        GapNarrator narrator = mock(GapNarrator.class);
        when(narrator.isAvailable()).thenReturn(false);
        QuotaService quotaService = mock(QuotaService.class);
        GetGapsTool tool = new GetGapsTool(gapsApi, narrator, quotaService);

        authenticate(userA);
        GapsView viewA = tool.getGaps("en", new ToolContext(Map.of(AgentToolContext.CURRENT_USER_KEY, new CurrentUser(userA))));

        assertThat(captured.get()).isNotNull();
        assertThat(captured.get().getUserId()).isEqualTo(userA);
        assertThat(viewA.isReady()).isTrue();
        assertThat(viewA.getGaps()).extracting(GapConceptView::getConcept)
                .containsExactly("photosynthesis")
                .doesNotContain("derivatives");
        assertThat(viewA.getNarration()).isNull();
        verifyNoInteractions(quotaService);
    }

    @Test
    void getGapsForUserBDoesNotLeakUserAGaps() {
        AtomicReference<CurrentUser> captured = new AtomicReference<>();
        Map<UUID, GapsReportDto> byUser = new HashMap<>();
        byUser.put(userA, reportWithGap("photosynthesis"));
        byUser.put(userB, reportWithGap("derivatives"));
        GapNarrator narrator = mock(GapNarrator.class);
        when(narrator.isAvailable()).thenReturn(false);
        GetGapsTool tool = new GetGapsTool(
                new CapturingGapsApi(captured, byUser), narrator, mock(QuotaService.class));

        authenticate(userB);
        GapsView viewB = tool.getGaps("ru", new ToolContext(Map.of(AgentToolContext.CURRENT_USER_KEY, new CurrentUser(userB))));

        assertThat(captured.get().getUserId()).isEqualTo(userB);
        assertThat(viewB.getGaps()).extracting(GapConceptView::getConcept)
                .containsExactly("derivatives")
                .doesNotContain("photosynthesis");
    }

    @Test
    void getGapsReturnsNotReadyWhenUserHasNoReport() {
        GetGapsTool tool = new GetGapsTool(
                new CapturingGapsApi(new AtomicReference<>(), new HashMap<>()),
                mock(GapNarrator.class),
                mock(QuotaService.class));

        authenticate(userA);
        GapsView view = tool.getGaps("en", new ToolContext(Map.of(AgentToolContext.CURRENT_USER_KEY, new CurrentUser(userA))));

        assertThat(view.isReady()).isFalse();
        assertThat(view.getGaps()).isEmpty();
        assertThat(view.getStrengths()).isEmpty();
        assertThat(view.getNarration()).isNull();
    }

    @Test
    void getGapsFailsWhenSecurityContextIsAnonymous() {
        GetGapsTool tool = new GetGapsTool(
                new CapturingGapsApi(new AtomicReference<>(), new HashMap<>()),
                mock(GapNarrator.class),
                mock(QuotaService.class));

        assertThatThrownBy(() -> tool.getGaps("en", null))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void getGapsExposesTrendAndRecommendedNextToTheModel() {
        Map<UUID, GapsReportDto> byUser = new HashMap<>();
        byUser.put(userA, reportWithGap("photosynthesis"));
        GapNarrator narrator = mock(GapNarrator.class);
        when(narrator.isAvailable()).thenReturn(false);
        GetGapsTool tool = new GetGapsTool(
                new CapturingGapsApi(new AtomicReference<>(), byUser), narrator, mock(QuotaService.class));

        authenticate(userA);
        GapsView view = tool.getGaps("en", new ToolContext(Map.of(AgentToolContext.CURRENT_USER_KEY, new CurrentUser(userA))));

        assertThat(view.getRecommendedNext()).isEqualTo("photosynthesis");
        assertThat(view.getGaps()).singleElement()
                .extracting(GapConceptView::getTrend)
                .isEqualTo("worsening");
    }

    private GapsReportDto reportWithGap(String concept) {
        GapItemDto gap = new GapItemDto(
                UUID.randomUUID(), concept, 3, 1, 0.25,
                OffsetDateTime.now(), UUID.randomUUID(), "Notes", GapTrend.WORSENING);
        return new GapsReportDto(OffsetDateTime.now(), 4, 1, 0.25, "summary",
                List.of(gap), List.of(), concept);
    }

    private void authenticate(UUID userId) {
        CurrentUser principal = new CurrentUser(userId);
        TestingAuthenticationToken auth = new TestingAuthenticationToken(principal, null);
        auth.setAuthenticated(true);
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    private static final class CapturingGapsApi implements GapsApi {

        private final AtomicReference<CurrentUser> captured;
        private final Map<UUID, GapsReportDto> byUser;

        private CapturingGapsApi(AtomicReference<CurrentUser> captured, Map<UUID, GapsReportDto> byUser) {
            this.captured = captured;
            this.byUser = byUser;
        }

        @Override
        public Optional<GapsReportDto> get(CurrentUser currentUser) {
            return refreshAndGet(currentUser);
        }

        @Override
        public Optional<GapsReportDto> get(UUID userId) {
            return Optional.ofNullable(byUser.get(userId));
        }

        @Override
        public Optional<GapsReportDto> refreshAndGet(CurrentUser currentUser) {
            captured.set(currentUser);
            return Optional.ofNullable(byUser.get(currentUser.getUserId()));
        }
    }
}
