package org.aiincubator.ilmai.quiz.service;

import lombok.RequiredArgsConstructor;
import org.aiincubator.ilmai.common.CurrentUser;
import org.aiincubator.ilmai.ai.RetrievalApi;
import org.aiincubator.ilmai.ai.RetrievedChunkDto;
import org.aiincubator.ilmai.common.i18n.SupportedLocale;
import org.aiincubator.ilmai.common.quota.QuotaService;
import org.aiincubator.ilmai.materials.MaterialDto;
import org.aiincubator.ilmai.materials.MaterialsApi;
import org.aiincubator.ilmai.materials.TopicDto;
import org.aiincubator.ilmai.profiles.ProfileDto;
import org.aiincubator.ilmai.profiles.ProfilesApi;
import org.aiincubator.ilmai.rooms.RoomDto;
import org.aiincubator.ilmai.rooms.RoomGoalDto;
import org.aiincubator.ilmai.rooms.RoomsApi;
import org.aiincubator.ilmai.quiz.QuizAnswerGradedEvent;
import org.aiincubator.ilmai.quiz.domain.QuizDifficulty;
import org.aiincubator.ilmai.quiz.domain.QuizQuestion;
import org.aiincubator.ilmai.quiz.domain.QuizSession;
import org.aiincubator.ilmai.quiz.domain.QuizSessionRepository;
import org.aiincubator.ilmai.quiz.domain.QuizStatus;
import org.aiincubator.ilmai.quiz.payload.QuizQuestionResponse;
import org.aiincubator.ilmai.quiz.payload.QuizSessionResponse;
import org.aiincubator.ilmai.quiz.payload.StartQuizRequest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class QuizService {

    private static final int DEFAULT_QUESTION_COUNT = 5;
    private static final int MAX_QUESTION_COUNT = 20;
    private static final int MIN_DIFFICULTY_LEVEL = 1;
    private static final int MAX_DIFFICULTY_LEVEL = 5;

    private final QuizSessionRepository sessions;
    private final MaterialsApi materialsApi;
    private final ProfilesApi profilesApi;
    private final RoomsApi roomsApi;
    private final RetrievalApi retrievalApi;
    private final QuizGenerator quizGenerator;
    private final QuizGrader quizGrader;
    private final QuizMapper quizMapper;
    private final QuotaService quotaService;
    private final ApplicationEventPublisher events;

    @Transactional
    public QuizSessionResponse start(CurrentUser currentUser, StartQuizRequest request) {
        return start(currentUser, request, null);
    }

    @Transactional
    public QuizSessionResponse start(CurrentUser currentUser, StartQuizRequest request, String scopeQueryOverride) {
        int dailyQuota = quotaService.dailyQuizQuota(currentUser.getUserId());
        ProfileDto profile = profilesApi.require(currentUser.getUserId());
        SupportedLocale locale = resolveLocale(request != null ? request.getLocale() : null, profile);
        QuizDifficulty difficulty = resolveDifficulty(request != null ? request.getDifficulty() : null);
        int questionCount = clamp(request == null || request.getQuestionCount() == null
                ? DEFAULT_QUESTION_COUNT : request.getQuestionCount(), 1, MAX_QUESTION_COUNT);
        TopicDto topic = null;
        if (request != null && request.getTopicId() != null) {
            topic = materialsApi.findTopicOwnedByUser(request.getTopicId(), currentUser.getUserId())
                    .orElseThrow(() -> new QuizException(QuizException.Reason.QUIZ_NOT_FOUND));
        }
        if (!materialsApi.hasReadyMaterialsForUser(currentUser.getUserId())) {
            throw new QuizException(QuizException.Reason.QUIZ_MATERIALS_MISSING);
        }
        if (dailyQuota > 0) {
            long todayCount = sessions.countByUserIdAndStartedAtAfter(currentUser.getUserId(), startOfToday());
            if (todayCount >= dailyQuota) {
                throw new QuizException(QuizException.Reason.QUIZ_QUOTA_EXCEEDED);
            }
        }

        UUID roomId = resolveRoomId(currentUser, request);
        String goal = roomsApi.findGoal(roomId)
                .map(RoomGoalDto::getGoal).orElse(null);
        String retrievalQuery = resolveRetrievalQuery(scopeQueryOverride, topic, goal);
        List<RetrievedChunkDto> chunks = retrievalApi.retrieve(currentUser.getUserId(), roomId, retrievalQuery);
        if (chunks.isEmpty()) {
            chunks = sampleFromMaterials(currentUser.getUserId(), questionCount);
        }

        QuizSession session = new QuizSession();
        session.setUserId(currentUser.getUserId());
        session.setRoomId(roomId);
        session.setTopicId(topic == null ? null : topic.getId());
        session.setDifficulty(difficulty);
        session.setDifficultyLevel(seedDifficultyLevel(difficulty));
        session.setLocale(locale);
        session.setStatus(QuizStatus.IN_PROGRESS);
        QuizSession saved = sessions.save(session);

        List<QuestionDraft> drafts = quizGenerator.generate(difficulty, locale, questionCount, chunks);
        int position = 1;
        for (QuestionDraft draft : drafts) {
            UUID citationMaterialId = resolveCitation(draft, currentUser.getUserId(), chunks);
            if (citationMaterialId == null) {
                continue;
            }
            QuizQuestion q = new QuizQuestion();
            q.setSession(saved);
            q.setPosition(position++);
            q.setType(draft.getType());
            q.setConcept(draft.getConcept());
            q.setPrompt(draft.getPrompt());
            q.setOptions(draft.getOptions());
            q.setCorrectAnswer(draft.getCorrectAnswer());
            q.setExplanation(draft.getExplanation());
            q.setChunkIndex(draft.getChunkIndex());
            q.setMaterialId(citationMaterialId);
            saved.getQuestions().add(q);
        }
        saved.setTotalCount(saved.getQuestions().size());
        sessions.save(saved);
        profilesApi.incrementQuizCount(currentUser.getUserId());
        return quizMapper.toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<QuizSessionResponse> list(CurrentUser currentUser) {
        return sessions.findAllByUserIdOrderByCreatedAtDesc(currentUser.getUserId()).stream()
                .map(quizMapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public QuizSessionResponse get(CurrentUser currentUser, UUID sessionId) {
        QuizSession session = require(currentUser, sessionId);
        return quizMapper.toResponse(session);
    }

    @Transactional
    public QuizQuestionResponse answer(CurrentUser currentUser, UUID sessionId, UUID questionId, String userAnswer) {
        QuizSession session = require(currentUser, sessionId);
        QuizQuestion question = session.getQuestions().stream()
                .filter(q -> q.getId().equals(questionId))
                .findFirst()
                .orElseThrow(() -> new QuizException(QuizException.Reason.QUIZ_QUESTION_NOT_FOUND));
        applyAnswer(currentUser, session, question, userAnswer);
        return quizMapper.toResponse(question);
    }

    @Transactional
    public QuizGradeOutcome gradeByPosition(CurrentUser currentUser, UUID sessionId, int questionNumber,
                                            String userAnswer) {
        QuizSession session = require(currentUser, sessionId);
        QuizQuestion question = session.getQuestions().stream()
                .filter(q -> q.getPosition() == questionNumber)
                .findFirst()
                .orElseThrow(() -> new QuizException(QuizException.Reason.QUIZ_QUESTION_NOT_FOUND));
        applyAnswer(currentUser, session, question, userAnswer);
        long answeredCount = session.getQuestions().stream().filter(q -> q.getIsCorrect() != null).count();
        return new QuizGradeOutcome(question.getIsCorrect(), question.getFeedback(), question.getCorrectAnswer(),
                question.getExplanation(), question.getConcept(), question.getPosition(), (int) answeredCount,
                session.getTotalCount(), session.getCorrectCount(),
                session.getStatus() == QuizStatus.COMPLETED, session.getDifficultyLevel());
    }

    @Transactional(readOnly = true)
    public QuizPollSpec resolveByPosition(CurrentUser currentUser, UUID sessionId, int questionNumber) {
        QuizSession session = require(currentUser, sessionId);
        QuizQuestion question = session.getQuestions().stream()
                .filter(q -> q.getPosition() == questionNumber)
                .findFirst()
                .orElse(null);
        if (question == null) {
            return null;
        }
        Integer correctOptionId = quizGrader.correctOptionIndex(question);
        if (correctOptionId == null) {
            return null;
        }
        return new QuizPollSpec(correctOptionId, question.getExplanation());
    }

    private void applyAnswer(CurrentUser currentUser, QuizSession session, QuizQuestion question, String userAnswer) {
        if (question.getIsCorrect() != null) {
            throw new QuizException(QuizException.Reason.QUIZ_ALREADY_ANSWERED);
        }
        QuizGradeResult result = quizGrader.grade(question, userAnswer);
        question.setUserAnswer(userAnswer == null ? null : userAnswer.trim());
        question.setIsCorrect(result.getCorrect());
        question.setFeedback(result.getFeedback());

        int delta = Boolean.TRUE.equals(result.getCorrect()) ? 1 : -1;
        session.setDifficultyLevel(clamp(session.getDifficultyLevel() + delta,
                MIN_DIFFICULTY_LEVEL, MAX_DIFFICULTY_LEVEL));

        long answeredCount = session.getQuestions().stream().filter(q -> q.getIsCorrect() != null).count();
        long correctCount = session.getQuestions().stream().filter(q -> Boolean.TRUE.equals(q.getIsCorrect())).count();
        session.setCorrectCount((int) correctCount);
        if (answeredCount == session.getTotalCount() && session.getTotalCount() > 0) {
            session.setStatus(QuizStatus.COMPLETED);
            session.setCompletedAt(OffsetDateTime.now());
            session.setScore(session.getTotalCount() == 0 ? 0.0 : (double) correctCount / session.getTotalCount());
            profilesApi.incrementSessionsCount(currentUser.getUserId());
        }
        profilesApi.touchActivity(currentUser.getUserId());

        events.publishEvent(new QuizAnswerGradedEvent(
                currentUser.getUserId(),
                session.getId(),
                question.getId(),
                question.getMaterialId(),
                question.getConcept(),
                Boolean.TRUE.equals(question.getIsCorrect()),
                OffsetDateTime.now()));
    }

    @Transactional
    public QuizSessionResponse abandon(CurrentUser currentUser, UUID sessionId) {
        QuizSession session = require(currentUser, sessionId);
        if (session.getStatus() == QuizStatus.IN_PROGRESS) {
            session.setStatus(QuizStatus.ABANDONED);
            session.setCompletedAt(OffsetDateTime.now());
        }
        return quizMapper.toResponse(session);
    }

    private UUID resolveRoomId(CurrentUser currentUser, StartQuizRequest request) {
        if (request != null && request.getRoomId() != null) {
            return roomsApi.requireMember(currentUser, request.getRoomId()).getId();
        }
        return roomsApi.findPersonalForUser(currentUser.getUserId())
                .map(RoomDto::getId)
                .orElseThrow(() -> new QuizException(QuizException.Reason.QUIZ_MATERIALS_MISSING));
    }

    private QuizSession require(CurrentUser currentUser, UUID sessionId) {
        return sessions.findByIdAndUserId(sessionId, currentUser.getUserId())
                .orElseThrow(() -> new QuizException(QuizException.Reason.QUIZ_NOT_FOUND));
    }

    private QuizDifficulty resolveDifficulty(String value) {
        if (value == null || value.isBlank()) {
            return QuizDifficulty.SOLID;
        }
        try {
            return QuizDifficulty.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return QuizDifficulty.SOLID;
        }
    }

    private int seedDifficultyLevel(QuizDifficulty difficulty) {
        return clamp(difficulty.ordinal() + 1, MIN_DIFFICULTY_LEVEL, MAX_DIFFICULTY_LEVEL);
    }

    private SupportedLocale resolveLocale(String tag, ProfileDto profile) {
        if (tag != null && !tag.isBlank()) {
            return SupportedLocale.fromLanguageTag(tag).orElse(profile.getLocale());
        }
        return profile.getLocale();
    }

    private String resolveRetrievalQuery(String scopeQueryOverride, TopicDto topic, String goal) {
        if (scopeQueryOverride != null && !scopeQueryOverride.isBlank()) {
            return scopeQueryOverride.trim();
        }
        if (topic != null) {
            return topic.getName();
        }
        return goal != null ? goal : "general review";
    }

    private UUID resolveCitation(QuestionDraft draft, UUID userId, List<RetrievedChunkDto> chunks) {
        if (draft.getMaterialId() != null) {
            Optional<MaterialDto> owned = materialsApi.findOwnedByUser(draft.getMaterialId(), userId);
            if (owned.isPresent()) {
                return owned.get().getId();
            }
        }
        return chunks.stream()
                .map(RetrievedChunkDto::getMaterialId)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    private List<RetrievedChunkDto> sampleFromMaterials(UUID userId, int desired) {
        List<MaterialDto> ready = materialsApi.findReadyForUser(userId);
        if (ready.isEmpty()) {
            return List.of();
        }
        int limit = Math.min(desired, ready.size());
        List<RetrievedChunkDto> chunks = new java.util.ArrayList<>(limit);
        for (int i = 0; i < limit; i++) {
            MaterialDto m = ready.get(i);
            chunks.add(new RetrievedChunkDto(m.getId(), m.getTitle(), 0, m.getTitle(), null));
        }
        return chunks;
    }

    private OffsetDateTime startOfToday() {
        return LocalDate.now(ZoneOffset.UTC).atStartOfDay().atOffset(ZoneOffset.UTC);
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
