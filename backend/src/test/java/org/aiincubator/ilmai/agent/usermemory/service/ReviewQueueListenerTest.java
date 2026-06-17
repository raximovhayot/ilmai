package org.aiincubator.ilmai.agent.usermemory.service;

import org.aiincubator.ilmai.quiz.QuizAnswerGradedEvent;
import org.aiincubator.ilmai.agent.usermemory.domain.ReviewQueueEntry;
import org.aiincubator.ilmai.agent.usermemory.domain.ReviewQueueRepository;
import org.aiincubator.ilmai.agent.usermemory.domain.ReviewStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReviewQueueListenerTest {

    @Mock ReviewQueueRepository reviewQueue;

    @InjectMocks ReviewQueueListener listener;

    private final UUID userId = UUID.randomUUID();
    private final OffsetDateTime gradedAt = OffsetDateTime.parse("2026-05-31T10:00:00Z");

    @Test
    void wrongAnswer_createsEntryScheduledOneDayOut() {
        when(reviewQueue.findByUserIdAndConcept(userId, "photosynthesis")).thenReturn(Optional.empty());

        listener.onQuizAnswerGraded(wrong("photosynthesis"));

        ReviewQueueEntry saved = captureSaved();
        assertThat(saved.getUserId()).isEqualTo(userId);
        assertThat(saved.getConcept()).isEqualTo("photosynthesis");
        assertThat(saved.getStatus()).isEqualTo(ReviewStatus.ACTIVE);
        assertThat(saved.getIntervalIndex()).isZero();
        assertThat(saved.getNextReviewAt()).isEqualTo(gradedAt.plusDays(1));
        assertThat(saved.getTimesWrong()).isEqualTo(1);
    }

    @Test
    void wrongAnswer_onExistingEntry_resetsLadderToOneDay() {
        ReviewQueueEntry existing = entry("krebs cycle", 2, ReviewStatus.ACTIVE);
        existing.setTimesWrong(1);
        when(reviewQueue.findByUserIdAndConcept(userId, "krebs cycle")).thenReturn(Optional.of(existing));

        listener.onQuizAnswerGraded(wrong("krebs cycle"));

        ReviewQueueEntry saved = captureSaved();
        assertThat(saved.getIntervalIndex()).isZero();
        assertThat(saved.getNextReviewAt()).isEqualTo(gradedAt.plusDays(1));
        assertThat(saved.getTimesWrong()).isEqualTo(2);
        assertThat(saved.getStatus()).isEqualTo(ReviewStatus.ACTIVE);
    }

    @Test
    void correctAnswer_onExistingEntry_advancesLadderToThreeDays() {
        ReviewQueueEntry existing = entry("enzymes", 0, ReviewStatus.ACTIVE);
        when(reviewQueue.findByUserIdAndConcept(userId, "enzymes")).thenReturn(Optional.of(existing));

        listener.onQuizAnswerGraded(correct("enzymes"));

        ReviewQueueEntry saved = captureSaved();
        assertThat(saved.getIntervalIndex()).isEqualTo(1);
        assertThat(saved.getNextReviewAt()).isEqualTo(gradedAt.plusDays(3));
        assertThat(saved.getTimesCorrect()).isEqualTo(1);
        assertThat(saved.getStatus()).isEqualTo(ReviewStatus.ACTIVE);
    }

    @Test
    void correctAnswer_atTopOfLadder_marksMastered() {
        ReviewQueueEntry existing = entry("mitosis", 3, ReviewStatus.ACTIVE);
        when(reviewQueue.findByUserIdAndConcept(userId, "mitosis")).thenReturn(Optional.of(existing));

        listener.onQuizAnswerGraded(correct("mitosis"));

        ReviewQueueEntry saved = captureSaved();
        assertThat(saved.getStatus()).isEqualTo(ReviewStatus.MASTERED);
        assertThat(saved.getIntervalIndex()).isEqualTo(3);
        assertThat(saved.getTimesCorrect()).isEqualTo(1);
    }

    @Test
    void correctAnswer_withNoExistingEntry_doesNothing() {
        when(reviewQueue.findByUserIdAndConcept(userId, "osmosis")).thenReturn(Optional.empty());

        listener.onQuizAnswerGraded(correct("osmosis"));

        verify(reviewQueue, never()).save(any());
    }

    @Test
    void blankConcept_isIgnored() {
        listener.onQuizAnswerGraded(new QuizAnswerGradedEvent(
                userId, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "  ", false, gradedAt));

        verifyNoInteractions(reviewQueue);
    }

    private QuizAnswerGradedEvent wrong(String concept) {
        return new QuizAnswerGradedEvent(userId, UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), concept, false, gradedAt);
    }

    private QuizAnswerGradedEvent correct(String concept) {
        return new QuizAnswerGradedEvent(userId, UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), concept, true, gradedAt);
    }

    private ReviewQueueEntry entry(String concept, int intervalIndex, ReviewStatus status) {
        ReviewQueueEntry entry = new ReviewQueueEntry();
        entry.setUserId(userId);
        entry.setConcept(concept);
        entry.setIntervalIndex(intervalIndex);
        entry.setStatus(status);
        entry.setNextReviewAt(gradedAt);
        return entry;
    }

    private ReviewQueueEntry captureSaved() {
        ArgumentCaptor<ReviewQueueEntry> captor = ArgumentCaptor.forClass(ReviewQueueEntry.class);
        verify(reviewQueue).save(captor.capture());
        return captor.getValue();
    }
}
