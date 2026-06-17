package org.aiincubator.ilmai.quiz.service;

import lombok.RequiredArgsConstructor;
import org.aiincubator.ilmai.common.CurrentUser;
import org.aiincubator.ilmai.quiz.QuizApi;
import org.aiincubator.ilmai.quiz.QuizCardDto;
import org.aiincubator.ilmai.quiz.QuizGradeDto;
import org.aiincubator.ilmai.quiz.QuizGradeException;
import org.aiincubator.ilmai.quiz.QuizGradeReason;
import org.aiincubator.ilmai.quiz.QuizPollSpecDto;
import org.aiincubator.ilmai.quiz.QuizQuestionDto;
import org.aiincubator.ilmai.quiz.QuizSessionDto;
import org.aiincubator.ilmai.quiz.QuizUnavailableException;
import org.aiincubator.ilmai.quiz.WeeklyQuizStats;
import org.aiincubator.ilmai.quiz.QuizUnavailableReason;
import org.aiincubator.ilmai.quiz.domain.QuizQuestionRepository;
import org.aiincubator.ilmai.quiz.domain.QuizSessionRepository;
import org.aiincubator.ilmai.quiz.payload.QuizSessionResponse;
import org.aiincubator.ilmai.quiz.payload.StartQuizRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DefaultQuizApi implements QuizApi {

    private final QuizQuestionRepository quizQuestions;
    private final QuizSessionRepository sessions;
    private final QuizApiMapper quizApiMapper;
    private final QuizService quizService;
    private final QuizCardMapper quizCardMapper;
    private final QuizGradeMapper quizGradeMapper;

    @Override
    @Transactional(readOnly = true)
    public List<QuizQuestionDto> findIncorrectQuestionsForUser(UUID userId) {
        return quizApiMapper.toQuestionDtoList(quizQuestions.findAllBySessionUserIdAndIsCorrectFalse(userId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<QuizSessionDto> findAllSessionsForUser(UUID userId) {
        return quizApiMapper.toSessionDtoList(sessions.findAllByUserIdOrderByCreatedAtDesc(userId));
    }

    @Override
    @Transactional(readOnly = true)
    public WeeklyQuizStats weeklyStats(UUID userId, OffsetDateTime since) {
        int quizzes = (int) sessions.countByUserIdAndStartedAtAfter(userId, since);
        int answered = (int) quizQuestions.countBySessionUserIdAndIsCorrectIsNotNullAndUpdatedAtAfter(userId, since);
        int correct = (int) quizQuestions.countBySessionUserIdAndIsCorrectIsTrueAndUpdatedAtAfter(userId, since);
        return new WeeklyQuizStats(quizzes, answered, correct);
    }

    @Override
    public QuizCardDto startQuiz(CurrentUser currentUser, String scope, Integer questionCount, String difficulty) {
        StartQuizRequest request = new StartQuizRequest();
        request.setDifficulty(difficulty);
        request.setQuestionCount(questionCount);
        QuizSessionResponse response;
        try {
            response = quizService.start(currentUser, request, scope);
        } catch (QuizException ex) {
            throw new QuizUnavailableException(translate(ex.getReason()));
        }
        if (response.getQuestions() == null || response.getQuestions().isEmpty()) {
            throw new QuizUnavailableException(QuizUnavailableReason.NO_QUESTIONS);
        }
        return quizCardMapper.toCard(response);
    }

    @Override
    public QuizGradeDto gradeAnswer(CurrentUser currentUser, UUID sessionId, int questionNumber, String answer) {
        try {
            return quizGradeMapper.toDto(quizService.gradeByPosition(currentUser, sessionId, questionNumber, answer));
        } catch (QuizException ex) {
            throw new QuizGradeException(translateGrade(ex.getReason()));
        }
    }

    @Override
    @Transactional(readOnly = true)
    public QuizPollSpecDto resolveQuizPoll(CurrentUser currentUser, UUID sessionId, int questionNumber) {
        QuizPollSpec spec;
        try {
            spec = quizService.resolveByPosition(currentUser, sessionId, questionNumber);
        } catch (QuizException ex) {
            return null;
        }
        if (spec == null) {
            return null;
        }
        return new QuizPollSpecDto(spec.getCorrectOptionId(), spec.getExplanation());
    }

    private QuizUnavailableReason translate(QuizException.Reason reason) {
        return switch (reason) {
            case QUIZ_MATERIALS_MISSING -> QuizUnavailableReason.MATERIALS_MISSING;
            case QUIZ_QUOTA_EXCEEDED -> QuizUnavailableReason.QUOTA_EXCEEDED;
            default -> QuizUnavailableReason.NO_QUESTIONS;
        };
    }

    private QuizGradeReason translateGrade(QuizException.Reason reason) {
        return switch (reason) {
            case QUIZ_QUESTION_NOT_FOUND -> QuizGradeReason.QUESTION_NOT_FOUND;
            case QUIZ_ALREADY_ANSWERED -> QuizGradeReason.ALREADY_ANSWERED;
            default -> QuizGradeReason.SESSION_NOT_FOUND;
        };
    }
}
