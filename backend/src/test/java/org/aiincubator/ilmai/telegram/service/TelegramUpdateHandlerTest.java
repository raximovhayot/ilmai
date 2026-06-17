package org.aiincubator.ilmai.telegram.service;

import org.aiincubator.ilmai.agent.AgentApi;
import org.aiincubator.ilmai.agent.ChatChannel;
import org.aiincubator.ilmai.agent.MessagePart;
import org.aiincubator.ilmai.agent.QuizCardPart;
import org.aiincubator.ilmai.agent.TextPart;
import org.aiincubator.ilmai.common.i18n.MessageService;
import org.aiincubator.ilmai.common.i18n.SupportedLocale;
import org.aiincubator.ilmai.plan.PlanApi;
import org.aiincubator.ilmai.profiles.ProfileDto;
import org.aiincubator.ilmai.profiles.ProfilesApi;
import org.aiincubator.ilmai.quiz.QuizApi;
import org.aiincubator.ilmai.quiz.QuizGradeDto;
import org.aiincubator.ilmai.streaks.StreakDto;
import org.aiincubator.ilmai.streaks.StreaksApi;
import org.aiincubator.ilmai.telegram.config.TelegramProperties;
import org.aiincubator.ilmai.telegram.domain.TelegramQuizPoll;
import org.aiincubator.ilmai.telegram.domain.TelegramQuizPollRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.chat.Chat;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.api.objects.polls.PollAnswer;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TelegramUpdateHandlerTest {

    @Mock
    private TelegramProperties properties;
    @Mock
    private TelegramService telegramService;
    @Mock
    private TelegramApiClient telegramApiClient;
    @Mock
    private TelegramMessageFlattener flattener;
    @Mock
    private AgentApi agentApi;
    @Mock
    private PlanApi planApi;
    @Mock
    private StreaksApi streaksApi;
    @Mock
    private ProfilesApi profilesApi;
    @Mock
    private MessageService messageService;
    @Mock
    private QuizApi quizApi;
    @Mock
    private TelegramQuizPollRepository pollRepository;

    @InjectMocks
    private TelegramUpdateHandler handler;

    @BeforeEach
    void stubCopy() {
        lenient().when(messageService.get(anyString(), any(), any())).thenReturn("copy");
    }

    @Test
    void unlinkedChatFreeTextIsRejectedWithoutCallingCoach() {
        long chatId = 555L;
        when(telegramService.findLinkedUser(chatId)).thenReturn(Optional.empty());

        handler.handleUpdate(update(chatId, "what does my textbook say about photosynthesis?"));

        verify(telegramService).findLinkedUser(chatId);
        verify(telegramService, never()).markSeen(anyLong());
        verify(telegramApiClient, times(1)).sendMessage(eq(chatId), anyString());
        verifyNoInteractions(agentApi);
    }

    @Test
    void streakCommandUsesCheapPathWithoutCallingCoach() {
        long chatId = 777L;
        UUID userId = UUID.randomUUID();
        when(telegramService.findLinkedUser(chatId)).thenReturn(Optional.of(userId));
        when(profilesApi.find(userId)).thenReturn(Optional.of(profile(userId)));
        when(streaksApi.getStreak(userId)).thenReturn(new StreakDto(userId, 3, 7, null, null));

        handler.handleUpdate(update(chatId, "/streak"));

        verify(streaksApi).getStreak(userId);
        verify(telegramService).markSeen(chatId);
        verify(telegramApiClient, times(1)).sendMessage(eq(chatId), anyString());
        verifyNoInteractions(agentApi);
    }

    @Test
    void freeTextFromLinkedUserRunsCoachTurnAndSendsFlattenedReply() {
        long chatId = 999L;
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        when(telegramService.findLinkedUser(chatId)).thenReturn(Optional.of(userId));
        when(profilesApi.find(userId)).thenReturn(Optional.of(profile(userId)));
        when(agentApi.canonicalSession(any(), eq(ChatChannel.TELEGRAM))).thenReturn(sessionId);
        when(agentApi.chat(any(), eq(sessionId), eq("explain mitosis"), eq(ChatChannel.TELEGRAM)))
                .thenReturn(Flux.<MessagePart>just(new TextPart("Mitosis is cell division.")));
        when(flattener.flatten(any(), any())).thenReturn("Mitosis is cell division.");

        handler.handleUpdate(update(chatId, "explain mitosis"));

        verify(agentApi).chat(any(), eq(sessionId), eq("explain mitosis"), eq(ChatChannel.TELEGRAM));
        verify(telegramApiClient, times(1)).sendMessage(chatId, "Mitosis is cell division.");
    }

    @Test
    void coachTurnStreamsThrottledDraftsAndSendsFinalMessage() {
        long chatId = 1200L;
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        when(telegramService.findLinkedUser(chatId)).thenReturn(Optional.of(userId));
        when(profilesApi.find(userId)).thenReturn(Optional.of(profile(userId)));
        when(properties.getStreamThrottleMs()).thenReturn(0L);
        when(agentApi.canonicalSession(any(), eq(ChatChannel.TELEGRAM))).thenReturn(sessionId);
        when(agentApi.chat(any(), eq(sessionId), eq("explain mitosis"), eq(ChatChannel.TELEGRAM)))
                .thenReturn(Flux.<MessagePart>just(
                        new TextPart("Mitosis"), new TextPart("is cell division.")));
        when(flattener.flatten(any(), any())).thenReturn("Mitosis\n\nis cell division.");

        handler.handleUpdate(update(chatId, "explain mitosis"));

        verify(telegramApiClient, times(2)).streamMessage(eq(chatId), anyInt(), anyString());
        verify(telegramApiClient).sendMessage(chatId, "Mitosis\n\nis cell division.");
    }

    @Test
    void coachTurnThrottleSuppressesDraftsButStillSendsFinalMessage() {
        long chatId = 1201L;
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        when(telegramService.findLinkedUser(chatId)).thenReturn(Optional.of(userId));
        when(profilesApi.find(userId)).thenReturn(Optional.of(profile(userId)));
        when(properties.getStreamThrottleMs()).thenReturn(Long.MAX_VALUE);
        when(agentApi.canonicalSession(any(), eq(ChatChannel.TELEGRAM))).thenReturn(sessionId);
        when(agentApi.chat(any(), eq(sessionId), eq("explain mitosis"), eq(ChatChannel.TELEGRAM)))
                .thenReturn(Flux.<MessagePart>just(
                        new TextPart("Mitosis"), new TextPart("is cell division.")));
        when(flattener.flatten(any(), any())).thenReturn("Mitosis is cell division.");

        handler.handleUpdate(update(chatId, "explain mitosis"));

        verify(telegramApiClient, never()).streamMessage(anyLong(), anyInt(), anyString());
        verify(telegramApiClient).sendMessage(chatId, "Mitosis is cell division.");
    }

    @Test
    void quizCardFromCoachIsSentAsTelegramPollAndBindingPersisted() {
        long chatId = 1001L;
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        UUID questionId = UUID.randomUUID();
        List<String> options = List.of("Paris", "London", "Berlin", "Rome");
        QuizCardPart card = new QuizCardPart(sessionId, questionId, 1, "MULTIPLE_CHOICE",
                "Geography", "What is the capital of France?", options, UUID.randomUUID(), "Atlas", 0);
        when(telegramService.findLinkedUser(chatId)).thenReturn(Optional.of(userId));
        when(profilesApi.find(userId)).thenReturn(Optional.of(profile(userId)));
        when(agentApi.canonicalSession(any(), eq(ChatChannel.TELEGRAM))).thenReturn(sessionId);
        when(agentApi.chat(any(), eq(sessionId), eq("quiz me"), eq(ChatChannel.TELEGRAM)))
                .thenReturn(Flux.<MessagePart>just(card));
        when(telegramApiClient.sendPoll(eq(chatId), anyString(), anyList())).thenReturn("poll-1");

        handler.handleUpdate(update(chatId, "quiz me"));

        verify(telegramApiClient).sendPoll(chatId, "What is the capital of France?", options);
        ArgumentCaptor<TelegramQuizPoll> captor = ArgumentCaptor.forClass(TelegramQuizPoll.class);
        verify(pollRepository).save(captor.capture());
        TelegramQuizPoll saved = captor.getValue();
        assertThat(saved.getPollId()).isEqualTo("poll-1");
        assertThat(saved.getSessionId()).isEqualTo(sessionId);
        assertThat(saved.getQuestionId()).isEqualTo(questionId);
        assertThat(saved.getPosition()).isEqualTo(1);
        assertThat(saved.getOptions()).isEqualTo(options);
        assertThat(saved.getChatId()).isEqualTo(chatId);
        assertThat(saved.getUserId()).isEqualTo(userId);
        verify(telegramApiClient, never()).sendMessage(eq(chatId), anyString());
    }

    @Test
    void pollAnswerGradesQuizByOptionTextAndSendsResult() {
        long chatId = 1002L;
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        TelegramQuizPoll binding = new TelegramQuizPoll();
        binding.setPollId("poll-9");
        binding.setUserId(userId);
        binding.setChatId(chatId);
        binding.setSessionId(sessionId);
        binding.setQuestionId(UUID.randomUUID());
        binding.setPosition(1);
        binding.setOptions(List.of("Paris", "London"));
        when(pollRepository.findByPollId("poll-9")).thenReturn(Optional.of(binding));
        when(profilesApi.find(userId)).thenReturn(Optional.of(profile(userId)));
        when(quizApi.gradeAnswer(any(), eq(sessionId), eq(1), eq("Paris")))
                .thenReturn(new QuizGradeDto(true, "Correct!", "Paris", "Paris is the capital.",
                        "Geography", 1, 1, 5, 1, false, 1));

        handler.handleUpdate(pollAnswerUpdate("poll-9", 0));

        verify(quizApi).gradeAnswer(any(), eq(sessionId), eq(1), eq("Paris"));
        verify(pollRepository).save(binding);
        assertThat(binding.getAnsweredAt()).isNotNull();
        verify(telegramApiClient, times(1)).sendMessage(eq(chatId), anyString());
        verifyNoInteractions(agentApi);
    }

    @Test
    void pollAnswerForUnknownPollIsIgnored() {
        when(pollRepository.findByPollId("missing")).thenReturn(Optional.empty());

        handler.handleUpdate(pollAnswerUpdate("missing", 0));

        verifyNoInteractions(quizApi);
        verify(telegramApiClient, never()).sendMessage(anyLong(), anyString());
    }

    @Test
    void alreadyAnsweredPollIsNotGradedTwice() {
        TelegramQuizPoll binding = new TelegramQuizPoll();
        binding.setPollId("poll-done");
        binding.setUserId(UUID.randomUUID());
        binding.setChatId(1003L);
        binding.setSessionId(UUID.randomUUID());
        binding.setPosition(1);
        binding.setOptions(List.of("A", "B"));
        binding.setAnsweredAt(java.time.OffsetDateTime.now());
        when(pollRepository.findByPollId("poll-done")).thenReturn(Optional.of(binding));

        handler.handleUpdate(pollAnswerUpdate("poll-done", 1));

        verifyNoInteractions(quizApi);
        verify(telegramApiClient, never()).sendMessage(anyLong(), anyString());
    }

    @Test
    void callbackQueryFromLinkedUserAcksAndRunsCoachTurn() {
        long chatId = 1004L;
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        when(telegramService.findLinkedUser(chatId)).thenReturn(Optional.of(userId));
        when(profilesApi.find(userId)).thenReturn(Optional.of(profile(userId)));
        when(agentApi.canonicalSession(any(), eq(ChatChannel.TELEGRAM))).thenReturn(sessionId);
        when(agentApi.chat(any(), eq(sessionId), eq("copy"), eq(ChatChannel.TELEGRAM)))
                .thenReturn(Flux.<MessagePart>just(new TextPart("Quiz starting.")));
        when(flattener.flatten(any(), any())).thenReturn("Quiz starting.");

        handler.handleUpdate(callbackUpdate(chatId, "cb-1", "act:start_quiz"));

        verify(telegramApiClient).answerCallbackQuery("cb-1");
        verify(telegramService).markSeen(chatId);
        verify(agentApi).chat(any(), eq(sessionId), eq("copy"), eq(ChatChannel.TELEGRAM));
        verify(telegramApiClient).sendMessage(chatId, "Quiz starting.");
    }

    @Test
    void callbackQueryFromUnlinkedChatIsRejectedWithoutCallingCoach() {
        long chatId = 1005L;
        when(telegramService.findLinkedUser(chatId)).thenReturn(Optional.empty());

        handler.handleUpdate(callbackUpdate(chatId, "cb-2", "act:start_quiz"));

        verify(telegramApiClient).answerCallbackQuery("cb-2");
        verify(telegramService, never()).markSeen(anyLong());
        verifyNoInteractions(agentApi);
    }

    private ProfileDto profile(UUID userId) {
        return new ProfileDto(userId, SupportedLocale.EN, "Asia/Tashkent", null, null, null, null, 0, 0, 0, null);
    }

    private Update update(long chatId, String text) {
        Message message = Message.builder()
                .chat(Chat.builder().id(chatId).type("private").build())
                .text(text)
                .build();
        Update request = new Update();
        request.setMessage(message);
        return request;
    }

    private Update pollAnswerUpdate(String pollId, int optionId) {
        PollAnswer pollAnswer = new PollAnswer();
        pollAnswer.setPollId(pollId);
        pollAnswer.setOptionIds(List.of(optionId));
        Update request = new Update();
        request.setPollAnswer(pollAnswer);
        return request;
    }

    private Update callbackUpdate(long chatId, String callbackId, String data) {
        Message message = Message.builder()
                .chat(Chat.builder().id(chatId).type("private").build())
                .build();
        CallbackQuery callback = new CallbackQuery();
        callback.setId(callbackId);
        callback.setMessage(message);
        callback.setData(data);
        Update request = new Update();
        request.setCallbackQuery(callback);
        return request;
    }
}
