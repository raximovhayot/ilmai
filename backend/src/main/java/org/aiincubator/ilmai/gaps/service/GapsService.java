package org.aiincubator.ilmai.gaps.service;

import lombok.RequiredArgsConstructor;
import org.aiincubator.ilmai.common.CurrentUser;
import org.aiincubator.ilmai.gaps.GapTrend;
import org.aiincubator.ilmai.gaps.domain.KnowledgeGap;
import org.aiincubator.ilmai.gaps.domain.KnowledgeGapRepository;
import org.aiincubator.ilmai.gaps.payload.GapItem;
import org.aiincubator.ilmai.gaps.payload.GapsReportResponse;
import org.aiincubator.ilmai.materials.MaterialDto;
import org.aiincubator.ilmai.materials.MaterialsApi;
import org.aiincubator.ilmai.quiz.QuizApi;
import org.aiincubator.ilmai.quiz.QuizQuestionDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GapsService {

    private static final int MAX_ITEMS = 10;

    private final QuizApi quizApi;
    private final KnowledgeGapRepository gaps;
    private final GapsMapper gapsMapper;
    private final MaterialsApi materialsApi;

    @Transactional
    public GapsReportResponse refreshAndGet(CurrentUser currentUser) {
        UUID userId = currentUser.getUserId();
        Map<String, ConceptAggregate> aggregates = aggregateFromQuestions(userId);
        if (aggregates.isEmpty()) {
            throw new GapsException(GapsException.Reason.GAPS_NOT_READY);
        }
        OffsetDateTime now = OffsetDateTime.now();
        for (Map.Entry<String, ConceptAggregate> entry : aggregates.entrySet()) {
            String concept = entry.getKey();
            ConceptAggregate agg = entry.getValue();
            KnowledgeGap gap = gaps.findByUserIdAndConcept(userId, concept)
                    .orElseGet(() -> {
                        KnowledgeGap created = new KnowledgeGap();
                        created.setUserId(userId);
                        created.setConcept(concept);
                        return created;
                    });
            gap.setMissCount(agg.getMissCount());
            gap.setHitCount(agg.getHitCount());
            gap.setLastSeenAt(agg.getLastSeenAt() == null ? now : agg.getLastSeenAt());
            gap.setSuggestedMaterialId(agg.getSuggestedMaterialId());
            gap.setTrend(computeTrend(agg.getSamples()));
            gaps.save(gap);
        }
        return buildReport(userId, aggregates);
    }

    @Transactional(readOnly = true)
    public GapsReportResponse get(CurrentUser currentUser) {
        return get(currentUser.getUserId());
    }

    @Transactional(readOnly = true)
    public GapsReportResponse get(UUID userId) {
        List<KnowledgeGap> all = gaps.findAllByUserIdOrderByMissCountDescLastSeenAtDesc(userId);
        if (all.isEmpty()) {
            throw new GapsException(GapsException.Reason.GAPS_NOT_READY);
        }
        List<GapItem> gapItems = all.stream()
                .filter(g -> g.getMissCount() >= g.getHitCount())
                .limit(MAX_ITEMS)
                .map(gapsMapper::toResponse)
                .toList();
        List<GapItem> strengths = all.stream()
                .filter(g -> g.getHitCount() > g.getMissCount())
                .sorted(Comparator.comparingInt(KnowledgeGap::getHitCount).reversed())
                .limit(MAX_ITEMS)
                .map(gapsMapper::toResponse)
                .toList();
        int answered = all.stream().mapToInt(g -> g.getHitCount() + g.getMissCount()).sum();
        int correct = all.stream().mapToInt(KnowledgeGap::getHitCount).sum();
        double accuracy = answered == 0 ? 0.0 : (double) correct / answered;
        return new GapsReportResponse(OffsetDateTime.now(), answered, correct, accuracy,
                composeSummary(gapItems, strengths, accuracy), gapItems, strengths,
                recommendedNext(gapItems));
    }

    private Map<String, ConceptAggregate> aggregateFromQuestions(UUID userId) {
        List<QuizQuestionDto> wrong = quizApi.findIncorrectQuestionsForUser(userId);
        List<QuizQuestionDto> all = quizApi.findAllSessionsForUser(userId).stream()
                .flatMap(s -> s.getQuestions().stream())
                .filter(q -> q.getIsCorrect() != null)
                .toList();
        Map<String, ConceptAggregate> map = new HashMap<>();
        for (QuizQuestionDto q : all) {
            String concept = conceptKey(q);
            ConceptAggregate agg = map.computeIfAbsent(concept, k -> new ConceptAggregate());
            if (Boolean.TRUE.equals(q.getIsCorrect())) {
                agg.setHitCount(agg.getHitCount() + 1);
            } else {
                agg.setMissCount(agg.getMissCount() + 1);
            }
            agg.getSamples().add(new GapAnswerSample(q.getUpdatedAt(), Boolean.TRUE.equals(q.getIsCorrect())));
            OffsetDateTime updated = q.getUpdatedAt();
            if (agg.getLastSeenAt() == null || (updated != null && updated.isAfter(agg.getLastSeenAt()))) {
                agg.setLastSeenAt(updated);
            }
            if (agg.getSuggestedMaterialId() == null && q.getMaterialId() != null && !Boolean.TRUE.equals(q.getIsCorrect())) {
                agg.setSuggestedMaterialId(q.getMaterialId());
            }
        }
        for (QuizQuestionDto q : wrong) {
            ConceptAggregate agg = map.get(conceptKey(q));
            if (agg != null && agg.getSuggestedMaterialId() == null && q.getMaterialId() != null) {
                agg.setSuggestedMaterialId(q.getMaterialId());
            }
        }
        return map;
    }

    private GapsReportResponse buildReport(UUID userId, Map<String, ConceptAggregate> aggregates) {
        List<KnowledgeGap> current = gaps.findAllByUserIdOrderByMissCountDescLastSeenAtDesc(userId);
        List<GapItem> gapItems = current.stream()
                .filter(g -> g.getMissCount() > 0 && g.getMissCount() >= g.getHitCount())
                .limit(MAX_ITEMS)
                .map(gapsMapper::toResponse)
                .toList();
        List<GapItem> strengths = current.stream()
                .filter(g -> g.getHitCount() > g.getMissCount())
                .sorted(Comparator.comparingInt(KnowledgeGap::getHitCount).reversed())
                .limit(MAX_ITEMS)
                .map(gapsMapper::toResponse)
                .toList();
        int answered = aggregates.values().stream().mapToInt(a -> a.getHitCount() + a.getMissCount()).sum();
        int correct = aggregates.values().stream().mapToInt(ConceptAggregate::getHitCount).sum();
        double accuracy = answered == 0 ? 0.0 : (double) correct / answered;
        return new GapsReportResponse(OffsetDateTime.now(), answered, correct, accuracy,
                composeSummary(gapItems, strengths, accuracy), gapItems, strengths,
                recommendedNext(gapItems));
    }

    private String composeSummary(List<GapItem> gapItems, List<GapItem> strengths, double accuracy) {
        StringBuilder sb = new StringBuilder();
        sb.append("Overall accuracy: ").append(Math.round(accuracy * 100)).append("%. ");
        if (!strengths.isEmpty()) {
            sb.append("You're strongest on: ");
            for (int i = 0; i < strengths.size(); i++) {
                sb.append("'").append(strengths.get(i).getConcept()).append("'");
                if (i < strengths.size() - 1) sb.append(", ");
            }
            sb.append(". ");
        }
        if (!gapItems.isEmpty()) {
            sb.append("Concepts to revisit: ");
            for (int i = 0; i < gapItems.size(); i++) {
                sb.append("'").append(gapItems.get(i).getConcept()).append("'");
                if (i < gapItems.size() - 1) sb.append(", ");
            }
            sb.append(".");
        }
        return sb.toString();
    }

    private GapTrend computeTrend(List<GapAnswerSample> samples) {
        Map<Long, int[]> byWeek = new TreeMap<>();
        for (GapAnswerSample sample : samples) {
            if (sample.getAnsweredAt() == null) {
                continue;
            }
            long week = sample.getAnsweredAt().toLocalDate().toEpochDay() / 7;
            int[] cell = byWeek.computeIfAbsent(week, k -> new int[2]);
            if (sample.isCorrect()) {
                cell[0]++;
            }
            cell[1]++;
        }
        if (byWeek.size() < 2) {
            return GapTrend.FLAT;
        }
        double n = 0;
        double sumX = 0;
        double sumY = 0;
        double sumXx = 0;
        double sumXy = 0;
        for (Map.Entry<Long, int[]> entry : byWeek.entrySet()) {
            double x = entry.getKey();
            double y = (double) entry.getValue()[0] / entry.getValue()[1];
            n++;
            sumX += x;
            sumY += y;
            sumXx += x * x;
            sumXy += x * y;
        }
        double denominator = n * sumXx - sumX * sumX;
        if (denominator == 0) {
            return GapTrend.FLAT;
        }
        double slope = (n * sumXy - sumX * sumY) / denominator;
        if (slope > 1e-9) {
            return GapTrend.IMPROVING;
        }
        if (slope < -1e-9) {
            return GapTrend.WORSENING;
        }
        return GapTrend.FLAT;
    }

    private String recommendedNext(List<GapItem> gapItems) {
        return gapItems.stream()
                .min(Comparator.comparingDouble(GapItem::getAccuracy)
                        .thenComparingInt(item -> trendRank(item.getTrend()))
                        .thenComparing(GapItem::getLastSeenAt,
                                Comparator.nullsLast(Comparator.naturalOrder())))
                .map(GapItem::getConcept)
                .orElse(null);
    }

    private int trendRank(GapTrend trend) {
        if (trend == GapTrend.WORSENING) {
            return 0;
        }
        if (trend == GapTrend.IMPROVING) {
            return 2;
        }
        return 1;
    }

    private String conceptKey(QuizQuestionDto q) {
        if (q.getConcept() != null && !q.getConcept().isBlank()) {
            return q.getConcept().trim();
        }
        if (q.getMaterialId() != null) {
            String title = materialsApi.findById(q.getMaterialId()).map(MaterialDto::getTitle).orElse(null);
            if (title != null) {
                return title;
            }
        }
        return "general";
    }
}
