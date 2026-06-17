package org.aiincubator.ilmai.agent.usermemory.service;

import lombok.RequiredArgsConstructor;
import org.aiincubator.ilmai.quiz.QuizAnswerGradedEvent;
import org.aiincubator.ilmai.agent.usermemory.domain.ReviewQueueEntry;
import org.aiincubator.ilmai.agent.usermemory.domain.ReviewQueueRepository;
import org.aiincubator.ilmai.agent.usermemory.domain.ReviewStatus;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class ReviewQueueListener {

    static final int[] INTERVAL_DAYS = {1, 3, 7, 21};

    private final ReviewQueueRepository reviewQueue;

    @EventListener
    @Transactional(propagation = Propagation.MANDATORY)
    public void onQuizAnswerGraded(QuizAnswerGradedEvent event) {
        if (event.getConcept() == null || event.getConcept().isBlank()) {
            return;
        }
        String concept = event.getConcept().trim();
        Optional<ReviewQueueEntry> existing =
                reviewQueue.findByUserIdAndConcept(event.getUserId(), concept);

        if (event.isCorrect()) {
            advanceOnCorrect(existing, event);
        } else {
            resetOnWrong(existing, event, concept);
        }
    }

    private void advanceOnCorrect(Optional<ReviewQueueEntry> existing, QuizAnswerGradedEvent event) {
        if (existing.isEmpty()) {
            return;
        }
        ReviewQueueEntry entry = existing.get();
        if (entry.getStatus() != ReviewStatus.ACTIVE) {
            return;
        }
        entry.setTimesCorrect(entry.getTimesCorrect() + 1);
        if (entry.getIntervalIndex() >= INTERVAL_DAYS.length - 1) {
            entry.setStatus(ReviewStatus.MASTERED);
        } else {
            int nextIndex = entry.getIntervalIndex() + 1;
            entry.setIntervalIndex(nextIndex);
            entry.setNextReviewAt(event.getOccurredAt().plusDays(INTERVAL_DAYS[nextIndex]));
        }
        reviewQueue.save(entry);
    }

    private void resetOnWrong(Optional<ReviewQueueEntry> existing, QuizAnswerGradedEvent event, String concept) {
        ReviewQueueEntry entry = existing.orElseGet(() -> {
            ReviewQueueEntry created = new ReviewQueueEntry();
            created.setUserId(event.getUserId());
            created.setConcept(concept);
            return created;
        });
        entry.setStatus(ReviewStatus.ACTIVE);
        entry.setIntervalIndex(0);
        entry.setNextReviewAt(event.getOccurredAt().plusDays(INTERVAL_DAYS[0]));
        entry.setTimesWrong(entry.getTimesWrong() + 1);
        entry.setMaterialId(event.getMaterialId());
        entry.setLastQuestionId(event.getQuestionId());
        reviewQueue.save(entry);
    }
}
