package org.aiincubator.ilmai.gaps.service;

import org.aiincubator.ilmai.common.CurrentUser;
import org.aiincubator.ilmai.gaps.GapTrend;
import org.aiincubator.ilmai.gaps.domain.KnowledgeGap;
import org.aiincubator.ilmai.gaps.domain.KnowledgeGapRepository;
import org.aiincubator.ilmai.gaps.payload.GapItem;
import org.aiincubator.ilmai.gaps.payload.GapsReportResponse;
import org.aiincubator.ilmai.materials.MaterialsApi;
import org.aiincubator.ilmai.quiz.QuizApi;
import org.aiincubator.ilmai.quiz.QuizQuestionDto;
import org.aiincubator.ilmai.quiz.QuizSessionDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GapsServiceTest {

    private final UUID user = UUID.randomUUID();
    private final OffsetDateTime now = OffsetDateTime.now();

    private QuizApi quizApi;
    private KnowledgeGapRepository repository;
    private GapsMapper gapsMapper;
    private MaterialsApi materialsApi;
    private GapsService service;
    private List<KnowledgeGap> store;

    @BeforeEach
    void setUp() {
        quizApi = mock(QuizApi.class);
        repository = mock(KnowledgeGapRepository.class);
        gapsMapper = mock(GapsMapper.class);
        materialsApi = mock(MaterialsApi.class);
        service = new GapsService(quizApi, repository, gapsMapper, materialsApi);

        store = new ArrayList<>();
        when(quizApi.findIncorrectQuestionsForUser(any())).thenReturn(List.of());
        when(repository.findByUserIdAndConcept(any(), any())).thenReturn(Optional.empty());
        when(repository.save(any(KnowledgeGap.class))).thenAnswer(invocation -> {
            KnowledgeGap gap = invocation.getArgument(0);
            store.add(gap);
            return gap;
        });
        when(repository.findAllByUserIdOrderByMissCountDescLastSeenAtDesc(any())).thenReturn(store);
        when(gapsMapper.toResponse(any(KnowledgeGap.class))).thenAnswer(invocation -> toGapItem(invocation.getArgument(0)));
    }

    @Test
    void threeWrongAnswersOnSameConceptCreatesGap() {
        when(quizApi.findAllSessionsForUser(user)).thenReturn(List.of(session(
                question("Krebs cycle", false, now),
                question("Krebs cycle", false, now),
                question("Krebs cycle", false, now))));

        GapsReportResponse report = service.refreshAndGet(new CurrentUser(user));

        ArgumentCaptor<KnowledgeGap> captor = ArgumentCaptor.forClass(KnowledgeGap.class);
        verify(repository).save(captor.capture());
        KnowledgeGap saved = captor.getValue();
        assertThat(saved.getConcept()).isEqualTo("Krebs cycle");
        assertThat(saved.getMissCount()).isEqualTo(3);
        assertThat(saved.getHitCount()).isZero();
        assertThat(report.getGaps()).extracting(GapItem::getConcept).containsExactly("Krebs cycle");
    }

    @Test
    void trendIsImprovingWhenAccuracyRisesAcrossWeeks() {
        OffsetDateTime earlier = now.minusWeeks(2);
        when(quizApi.findAllSessionsForUser(user)).thenReturn(List.of(session(
                question("enzymes", false, earlier),
                question("enzymes", false, earlier),
                question("enzymes", true, now),
                question("enzymes", true, now))));

        GapsReportResponse report = service.refreshAndGet(new CurrentUser(user));

        assertThat(report.getGaps()).singleElement()
                .extracting(GapItem::getTrend)
                .isEqualTo(GapTrend.IMPROVING);
    }

    @Test
    void trendIsWorseningWhenAccuracyFallsAcrossWeeks() {
        OffsetDateTime earlier = now.minusWeeks(2);
        when(quizApi.findAllSessionsForUser(user)).thenReturn(List.of(session(
                question("kinetics", true, earlier),
                question("kinetics", true, earlier),
                question("kinetics", false, now),
                question("kinetics", false, now))));

        GapsReportResponse report = service.refreshAndGet(new CurrentUser(user));

        assertThat(report.getGaps()).singleElement()
                .extracting(GapItem::getTrend)
                .isEqualTo(GapTrend.WORSENING);
    }

    @Test
    void trendIsFlatWithinASingleWeek() {
        when(quizApi.findAllSessionsForUser(user)).thenReturn(List.of(session(
                question("osmosis", false, now),
                question("osmosis", false, now),
                question("osmosis", true, now))));

        GapsReportResponse report = service.refreshAndGet(new CurrentUser(user));

        assertThat(report.getGaps()).singleElement()
                .extracting(GapItem::getTrend)
                .isEqualTo(GapTrend.FLAT);
    }

    @Test
    void recommendedNextPicksTheLowestAccuracyGap() {
        when(quizApi.findAllSessionsForUser(user)).thenReturn(List.of(session(
                question("thermodynamics", false, now),
                question("thermodynamics", false, now),
                question("thermodynamics", false, now),
                question("optics", false, now),
                question("optics", false, now),
                question("optics", true, now))));

        GapsReportResponse report = service.refreshAndGet(new CurrentUser(user));

        assertThat(report.getGaps()).extracting(GapItem::getConcept)
                .containsExactlyInAnyOrder("thermodynamics", "optics");
        assertThat(report.getRecommendedNext()).isEqualTo("thermodynamics");
    }

    private QuizQuestionDto question(String concept, boolean correct, OffsetDateTime answeredAt) {
        return new QuizQuestionDto(UUID.randomUUID(), concept, null, correct, answeredAt);
    }

    private QuizSessionDto session(QuizQuestionDto... questions) {
        return new QuizSessionDto(UUID.randomUUID(), user, List.of(questions));
    }

    private GapItem toGapItem(KnowledgeGap gap) {
        int total = gap.getHitCount() + gap.getMissCount();
        double accuracy = total == 0 ? 0.0 : (double) gap.getHitCount() / total;
        return new GapItem(gap.getId(), gap.getConcept(), gap.getMissCount(), gap.getHitCount(),
                accuracy, gap.getLastSeenAt(), gap.getSuggestedMaterialId(), null, gap.getTrend());
    }
}
