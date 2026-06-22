package org.aiincubator.ilmai.telegram.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aiincubator.ilmai.agent.ActionPart;
import org.aiincubator.ilmai.agent.AgentApi;
import org.aiincubator.ilmai.agent.ChatChannel;
import org.aiincubator.ilmai.agent.ChatSessionSummary;
import org.aiincubator.ilmai.agent.MessagePart;
import org.aiincubator.ilmai.agent.QuizCardPart;
import org.aiincubator.ilmai.agent.TextPart;
import org.aiincubator.ilmai.common.CurrentUser;
import org.aiincubator.ilmai.common.i18n.MessageService;
import org.aiincubator.ilmai.common.i18n.SupportedLocale;
import org.aiincubator.ilmai.materials.MaterialDto;
import org.aiincubator.ilmai.materials.MaterialsApi;
import org.aiincubator.ilmai.plan.LearningPlanDto;
import org.aiincubator.ilmai.plan.PlanApi;
import org.aiincubator.ilmai.plan.PlanStepDto;
import org.aiincubator.ilmai.profiles.ProfileDto;
import org.aiincubator.ilmai.profiles.ProfilesApi;
import org.aiincubator.ilmai.quiz.QuizApi;
import org.aiincubator.ilmai.quiz.QuizGradeDto;
import org.aiincubator.ilmai.quiz.QuizPollSpecDto;
import org.aiincubator.ilmai.streaks.StreakDto;
import org.aiincubator.ilmai.streaks.StreaksApi;
import org.aiincubator.ilmai.telegram.config.TelegramProperties;
import org.aiincubator.ilmai.telegram.domain.TelegramQuizPoll;
import org.aiincubator.ilmai.telegram.domain.TelegramQuizPollRepository;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Document;
import org.telegram.telegrambots.meta.api.objects.File;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.message.MaybeInaccessibleMessage;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.api.objects.photo.PhotoSize;
import org.telegram.telegrambots.meta.api.objects.polls.PollAnswer;

import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Component
@RequiredArgsConstructor
@Slf4j
public class TelegramUpdateHandler {

    private static final String DEFAULT_TIMEZONE = "Asia/Tashkent";
    private static final Duration COACH_TIMEOUT = Duration.ofSeconds(120);
    private static final String ACTION_PREFIX = "act:";
    private static final String CHAT_NEW = "chat:new";
    private static final String CHAT_LIST = "chat:list";
    private static final String CHAT_SWITCH_PREFIX = "chat:switch:";
    private static final String FORGET_CONFIRM = "forget:confirm";
    private static final int POLL_QUESTION_MAX = 300;
    private static final int POLL_OPTION_MAX = 100;
    private static final int STREAM_TEXT_MAX = 4096;

    private final TelegramProperties properties;
    private final TelegramService telegramService;
    private final TelegramApiClient telegramApiClient;
    private final TelegramMessageFlattener flattener;
    private final AgentApi agentApi;
    private final PlanApi planApi;
    private final StreaksApi streaksApi;
    private final ProfilesApi profilesApi;
    private final MessageService messageService;
    private final QuizApi quizApi;
    private final TelegramQuizPollRepository pollRepository;
    private final MaterialsApi materialsApi;

    public void handleWebhook(String urlSecret, String headerSecret, Update update) {
        String expected = properties.getWebhookSecret();
        if (expected != null && !expected.isBlank()) {
            String actual = headerSecret != null ? headerSecret : urlSecret;
            if (!expected.equals(actual)) {
                throw new TelegramException(TelegramException.Reason.TELEGRAM_WEBHOOK_FORBIDDEN);
            }
        }
        handleUpdate(update);
    }

    public void handleUpdate(Update update) {
        if (update == null) {
            return;
        }
        if (update.getPollAnswer() != null) {
            handlePollAnswer(update.getPollAnswer());
            return;
        }
        if (update.getCallbackQuery() != null) {
            handleCallbackQuery(update.getCallbackQuery());
            return;
        }
        if (update.getMessage() == null) {
            return;
        }
        Message message = update.getMessage();
        var chat = message.getChat();
        if (chat == null || chat.getId() == null) {
            return;
        }
        long chatId = chat.getId();
        String text = message.getText() == null ? "" : message.getText().trim();
        Document document = message.getDocument();
        List<PhotoSize> photos = message.getPhoto();
        boolean hasAttachment = document != null || (photos != null && !photos.isEmpty());
        if (text.isEmpty() && !hasAttachment) {
            return;
        }
        Long from = message.getFrom() == null ? null : message.getFrom().getId();
        String username = message.getFrom() == null ? null : message.getFrom().getUserName();
        String command = text.toLowerCase(Locale.ROOT);

        if (!text.isEmpty() && command.startsWith("/start")) {
            handleStart(chatId, from, username, text);
            return;
        }

        Optional<UUID> linkedUser = telegramService.findLinkedUser(chatId);
        if (linkedUser.isEmpty()) {
            send(chatId, copy("telegram.bot.notLinked", SupportedLocale.DEFAULT));
            return;
        }
        UUID userId = linkedUser.get();
        runAsUser(userId, () ->
                handleLinkedMessage(chatId, userId, command, text, document, photos, hasAttachment));
    }

    private void handleLinkedMessage(long chatId, UUID userId, String command, String text,
                                     Document document, List<PhotoSize> photos, boolean hasAttachment) {
        if (command.equals("/unlink")) {
            handleUnlink(chatId, userId);
            return;
        }

        ProfileDto profile = profilesApi.find(userId).orElse(null);
        SupportedLocale locale = localeOf(profile);
        telegramService.markSeen(chatId);

        if (hasAttachment) {
            handleMaterialUpload(chatId, userId, locale, document, photos);
            return;
        }

        MenuAction menuAction = menuActionOf(text, locale);
        if (menuAction != null) {
            dispatchMenuAction(menuAction, chatId, userId, profile, locale);
            return;
        }

        if (command.equals("/help")) {
            dispatchMenuAction(MenuAction.HELP, chatId, userId, profile, locale);
        } else if (command.equals("/streak")) {
            dispatchMenuAction(MenuAction.STREAK, chatId, userId, profile, locale);
        } else if (command.equals("/today")) {
            dispatchMenuAction(MenuAction.TODAY, chatId, userId, profile, locale);
        } else if (command.equals("/quiz") || command.equals("/practice")) {
            dispatchMenuAction(MenuAction.QUIZ, chatId, userId, profile, locale);
        } else if (command.equals("/newchat") || command.equals("/new")) {
            dispatchMenuAction(MenuAction.NEW_CHAT, chatId, userId, profile, locale);
        } else if (command.equals("/chats")) {
            dispatchMenuAction(MenuAction.CHATS, chatId, userId, profile, locale);
        } else if (command.equals("/forget")) {
            dispatchMenuAction(MenuAction.FORGET, chatId, userId, profile, locale);
        } else if (command.startsWith("/")) {
            dispatchMenuAction(MenuAction.HELP, chatId, userId, profile, locale);
        } else {
            runCoachTurn(chatId, userId, locale, text);
        }
    }

    private MenuAction menuActionOf(String text, SupportedLocale locale) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String trimmed = text.strip();
        for (MenuAction action : MenuAction.values()) {
            if (trimmed.equals(copy(action.labelKey, locale))) {
                return action;
            }
        }
        return null;
    }

    private void dispatchMenuAction(MenuAction action, long chatId, UUID userId,
                                    ProfileDto profile, SupportedLocale locale) {
        switch (action) {
            case HELP -> sendMenu(chatId, copy("telegram.bot.help", locale), locale);
            case STREAK -> send(chatId, streakMessage(userId, locale));
            case TODAY -> send(chatId, todayMessage(userId, profile, locale));
            case QUIZ -> runCoachTurn(chatId, userId, locale, copy("telegram.bot.quizPrompt", locale));
            case NEW_CHAT -> handleNewChat(chatId, userId, locale);
            case CHATS -> handleChats(chatId, userId, locale);
            case FORGET -> handleForgetPrompt(chatId, locale);
        }
    }

    private void handleNewChat(long chatId, UUID userId, SupportedLocale locale) {
        agentApi.startNewSession(new CurrentUser(userId), ChatChannel.TELEGRAM);
        sendMenu(chatId, copy("telegram.bot.newchat.done", locale), locale);
    }

    private void handleChats(long chatId, UUID userId, SupportedLocale locale) {
        List<ChatSessionSummary> recent = agentApi.recentSessions(new CurrentUser(userId), ChatChannel.TELEGRAM);
        if (recent.isEmpty()) {
            send(chatId, copy("telegram.bot.chats.empty", locale));
            return;
        }
        List<InlineButton> buttons = new ArrayList<>();
        for (ChatSessionSummary session : recent) {
            String label = sessionLabel(session, locale);
            buttons.add(new InlineButton(label, CHAT_SWITCH_PREFIX + session.getId()));
        }
        buttons.add(new InlineButton(copy("telegram.bot.chats.newButton", locale), CHAT_NEW));
        telegramApiClient.sendMessage(chatId, copy("telegram.bot.chats.title", locale), buttons);
    }

    private void handleForgetPrompt(long chatId, SupportedLocale locale) {
        List<InlineButton> buttons = List.of(
                new InlineButton(copy("telegram.bot.forget.confirmButton", locale), FORGET_CONFIRM));
        telegramApiClient.sendMessage(chatId, copy("telegram.bot.forget.prompt", locale), buttons);
    }

    private void handleSwitch(long chatId, UUID userId, SupportedLocale locale, String sessionIdRaw) {
        UUID sessionId;
        try {
            sessionId = UUID.fromString(sessionIdRaw);
        } catch (IllegalArgumentException ex) {
            return;
        }
        CurrentUser currentUser = new CurrentUser(userId);
        try {
            agentApi.activateSession(currentUser, sessionId);
        } catch (RuntimeException ex) {
            log.warn("telegram chat switch failed user={} session={}: {}", userId, sessionId, ex.toString());
            return;
        }
        String title = agentApi.recentSessions(currentUser, ChatChannel.TELEGRAM).stream()
                .filter(session -> session.getId().equals(sessionId))
                .map(session -> sessionTitle(session, locale))
                .findFirst()
                .orElse(copy("telegram.bot.session.untitled", locale));
        sendMenu(chatId, copy("telegram.bot.chats.switched", locale, escapeHtml(title)), locale);
    }

    private void handleForget(long chatId, UUID userId, SupportedLocale locale) {
        agentApi.forgetActiveSession(new CurrentUser(userId), ChatChannel.TELEGRAM);
        send(chatId, copy("telegram.bot.forget.done", locale));
    }

    private String sessionTitle(ChatSessionSummary session, SupportedLocale locale) {
        String title = session.getTitle();
        if (title == null || title.isBlank()) {
            return copy("telegram.bot.session.untitled", locale);
        }
        return title.strip();
    }

    private String sessionLabel(ChatSessionSummary session, SupportedLocale locale) {
        String title = truncate(sessionTitle(session, locale), 48);
        if (session.isActive()) {
            return copy("telegram.bot.chats.activePrefix", locale, title);
        }
        return title;
    }

    private List<List<String>> menuRows(SupportedLocale locale) {
        return List.of(
                List.of(copy("telegram.bot.button.today", locale),
                        copy("telegram.bot.button.streak", locale),
                        copy("telegram.bot.button.quiz", locale)),
                List.of(copy("telegram.bot.button.newchat", locale),
                        copy("telegram.bot.button.chats", locale),
                        copy("telegram.bot.button.help", locale)));
    }

    private void sendMenu(long chatId, String text, SupportedLocale locale) {
        telegramApiClient.sendWithMenu(chatId, text, menuRows(locale));
    }

    private void runAsUser(UUID userId, Runnable action) {
        SecurityContext previous = SecurityContextHolder.getContext();
        try {
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(new UsernamePasswordAuthenticationToken(
                    new CurrentUser(userId), null, Collections.emptyList()));
            SecurityContextHolder.setContext(context);
            action.run();
        } finally {
            SecurityContextHolder.setContext(previous);
        }
    }

    private void handleStart(long chatId, Long from, String username, String text) {
        Optional<UUID> existing = telegramService.findLinkedUser(chatId);
        if (existing.isPresent()) {
            SupportedLocale existingLocale = localeOf(profilesApi.find(existing.get()).orElse(null));
            send(chatId, copy("telegram.bot.start.alreadyLinked", existingLocale));
            return;
        }
        String[] segments = text.split("\\s+", 2);
        if (segments.length < 2 || segments[1].isBlank()) {
            send(chatId, copy("telegram.bot.start.needCode", SupportedLocale.DEFAULT));
            return;
        }
        Optional<UUID> linked = telegramService.linkChat(chatId, from, username, segments[1].trim());
        if (linked.isEmpty()) {
            send(chatId, copy("telegram.bot.start.invalidCode", SupportedLocale.DEFAULT));
            return;
        }
        SupportedLocale locale = localeOf(profilesApi.find(linked.get()).orElse(null));
        sendMenu(chatId, copy("telegram.bot.start.linked", locale), locale);
    }

    private void handleMaterialUpload(long chatId, UUID userId, SupportedLocale locale,
                                      Document document, List<PhotoSize> photos) {
        String fileId;
        String filename;
        String contentType;
        if (document != null) {
            fileId = document.getFileId();
            filename = document.getFileName();
            contentType = document.getMimeType();
        } else {
            PhotoSize largest = largestPhoto(photos);
            fileId = largest == null ? null : largest.getFileId();
            filename = "telegram-photo.jpg";
            contentType = "image/jpeg";
        }
        if (fileId == null || fileId.isBlank()) {
            send(chatId, copy("telegram.bot.upload.failed", locale));
            return;
        }
        File file = telegramApiClient.getFile(fileId);
        byte[] content = telegramApiClient.downloadFile(file);
        if (content == null || content.length == 0) {
            send(chatId, copy("telegram.bot.upload.failed", locale));
            return;
        }
        if (filename == null || filename.isBlank()) {
            filename = "telegram-upload";
        }
        try {
            MaterialDto material = materialsApi.ingestUpload(userId, filename, contentType, content);
            send(chatId, copy("telegram.bot.upload.received", locale, escapeHtml(material.getTitle())));
        } catch (RuntimeException ex) {
            log.warn("telegram material ingest failed user={}: {}", userId, ex.toString());
            send(chatId, copy("telegram.bot.upload.failed", locale));
        }
    }

    private PhotoSize largestPhoto(List<PhotoSize> photos) {
        PhotoSize largest = null;
        long best = -1;
        for (PhotoSize photo : photos) {
            if (photo == null || photo.getFileId() == null) {
                continue;
            }
            long size = photo.getFileSize() != null ? photo.getFileSize()
                    : (long) (photo.getWidth() == null ? 0 : photo.getWidth())
                    * (photo.getHeight() == null ? 0 : photo.getHeight());
            if (size >= best) {
                best = size;
                largest = photo;
            }
        }
        return largest;
    }

    private void handleUnlink(long chatId, UUID userId) {
        SupportedLocale locale = localeOf(profilesApi.find(userId).orElse(null));
        telegramService.unlinkChat(chatId);
        send(chatId, copy("telegram.bot.unlinked", locale));
    }

    private void runCoachTurn(long chatId, UUID userId, SupportedLocale locale, String prompt) {
        CurrentUser currentUser = new CurrentUser(userId);
        List<MessagePart> parts;
        int draftId = nextDraftId();
        StringBuilder streamed = new StringBuilder();
        long[] lastDraftAt = {0L};
        long throttleMs = properties.getStreamThrottleMs();
        SecurityContext previous = SecurityContextHolder.getContext();
        try {
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(new UsernamePasswordAuthenticationToken(
                    currentUser, null, Collections.emptyList()));
            SecurityContextHolder.setContext(context);
            UUID sessionId = agentApi.canonicalSession(currentUser, ChatChannel.TELEGRAM);
            parts = agentApi.chat(currentUser, sessionId, prompt, ChatChannel.TELEGRAM)
                    .doOnNext(part -> {
                        if (part instanceof TextPart textPart
                                && textPart.getText() != null && !textPart.getText().isBlank()) {
                            if (streamed.length() > 0) {
                                streamed.append("\n\n");
                            }
                            streamed.append(textPart.getText().strip());
                            long now = System.currentTimeMillis();
                            if (now - lastDraftAt[0] >= throttleMs) {
                                lastDraftAt[0] = now;
                                telegramApiClient.streamMessage(chatId, draftId, draftText(streamed));
                            }
                        }
                    })
                    .collectList()
                    .block(COACH_TIMEOUT);
        } catch (RuntimeException ex) {
            log.warn("telegram coach turn failed user={}: {}", userId, ex.toString());
            send(chatId, copy("telegram.bot.error", locale));
            return;
        } finally {
            SecurityContextHolder.setContext(previous);
        }
        dispatchParts(chatId, userId, locale, parts);
    }

    private int nextDraftId() {
        int id = ThreadLocalRandom.current().nextInt();
        return id == 0 ? 1 : id;
    }

    private String draftText(StringBuilder streamed) {
        String text = streamed.toString();
        if (text.length() > STREAM_TEXT_MAX) {
            return text.substring(0, STREAM_TEXT_MAX - 1).strip() + "\u2026";
        }
        return text;
    }

    private void dispatchParts(long chatId, UUID userId, SupportedLocale locale, List<MessagePart> parts) {
        List<MessagePart> textParts = new ArrayList<>();
        List<QuizCardPart> pollCards = new ArrayList<>();
        List<ActionPart> actions = new ArrayList<>();
        if (parts != null) {
            for (MessagePart part : parts) {
                if (part instanceof QuizCardPart card && isPollable(card)) {
                    pollCards.add(card);
                } else if (part instanceof ActionPart action) {
                    actions.add(action);
                } else {
                    textParts.add(part);
                }
            }
        }
        String markdown = flattener.flatten(textParts, locale);
        if (markdown != null && !markdown.isBlank()) {
            String richMarkdown = flattener.flattenRaw(textParts, locale);
            telegramApiClient.sendRich(chatId, richMarkdown, markdown, buttonsFor(actions));
        } else if (pollCards.isEmpty()) {
            send(chatId, copy("telegram.bot.emptyReply", locale));
        }
        for (QuizCardPart card : pollCards) {
            sendQuizPoll(chatId, userId, card);
        }
    }

    private boolean isPollable(QuizCardPart card) {
        List<String> options = card.getOptions();
        return options != null && options.size() >= 2 && options.size() <= 10;
    }

    private void sendQuizPoll(long chatId, UUID userId, QuizCardPart card) {
        List<String> options = card.getOptions();
        List<String> labels = new ArrayList<>();
        for (String option : options) {
            labels.add(truncate(option, POLL_OPTION_MAX));
        }
        String question = truncate(card.getPrompt(), POLL_QUESTION_MAX);
        QuizPollSpecDto spec = quizApi.resolveQuizPoll(new CurrentUser(userId), card.getSessionId(), card.getPosition());
        String pollId;
        if (spec != null && spec.getCorrectOptionId() >= 0 && spec.getCorrectOptionId() < labels.size()) {
            pollId = telegramApiClient.sendQuizPoll(chatId, question, labels,
                    spec.getCorrectOptionId(), spec.getExplanation());
        } else {
            pollId = telegramApiClient.sendPoll(chatId, question, labels);
        }
        if (pollId == null) {
            return;
        }
        TelegramQuizPoll binding = new TelegramQuizPoll();
        binding.setPollId(pollId);
        binding.setUserId(userId);
        binding.setChatId(chatId);
        binding.setSessionId(card.getSessionId());
        binding.setQuestionId(card.getQuestionId());
        binding.setPosition(card.getPosition());
        binding.setOptions(options);
        pollRepository.save(binding);
    }

    private void handlePollAnswer(PollAnswer pollAnswer) {
        if (pollAnswer == null || pollAnswer.getPollId() == null) {
            return;
        }
        Optional<TelegramQuizPoll> bindingOpt = pollRepository.findByPollId(pollAnswer.getPollId());
        if (bindingOpt.isEmpty()) {
            return;
        }
        TelegramQuizPoll binding = bindingOpt.get();
        if (binding.getAnsweredAt() != null) {
            return;
        }
        List<Integer> optionIds = pollAnswer.getOptionIds();
        List<String> options = binding.getOptions();
        if (optionIds == null || optionIds.isEmpty() || options == null) {
            return;
        }
        int index = optionIds.get(0);
        if (index < 0 || index >= options.size()) {
            return;
        }
        long chatId = binding.getChatId();
        UUID userId = binding.getUserId();
        SupportedLocale locale = localeOf(profilesApi.find(userId).orElse(null));
        runAsUser(userId, () -> {
            CurrentUser currentUser = new CurrentUser(userId);
            QuizGradeDto grade;
            try {
                grade = quizApi.gradeAnswer(currentUser, binding.getSessionId(),
                        binding.getPosition(), options.get(index));
            } catch (RuntimeException ex) {
                log.warn("telegram poll grade failed poll={} user={}: {}",
                        binding.getPollId(), binding.getUserId(), ex.toString());
                binding.setAnsweredAt(OffsetDateTime.now());
                pollRepository.save(binding);
                send(chatId, copy("telegram.bot.quiz.expired", locale));
                return;
            }
            binding.setAnsweredAt(OffsetDateTime.now());
            pollRepository.save(binding);
            send(chatId, quizResultMessage(grade, locale));
        });
    }

    private String quizResultMessage(QuizGradeDto grade, SupportedLocale locale) {
        StringBuilder sb = new StringBuilder();
        if (Boolean.TRUE.equals(grade.getCorrect())) {
            sb.append(copy("telegram.bot.quiz.correct", locale, escapeHtml(grade.getFeedback())));
        } else {
            sb.append(copy("telegram.bot.quiz.wrong", locale, escapeHtml(grade.getFeedback())));
            if (grade.getCorrectAnswer() != null && !grade.getCorrectAnswer().isBlank()) {
                sb.append('\n').append(copy("telegram.bot.quiz.answer", locale,
                        escapeHtml(grade.getCorrectAnswer())));
            }
        }
        if (grade.getExplanation() != null && !grade.getExplanation().isBlank()) {
            sb.append("\n\n<i>").append(escapeHtml(grade.getExplanation())).append("</i>");
        }
        if (grade.isCompleted()) {
            sb.append("\n\n").append(copy("telegram.bot.quiz.completed", locale,
                    grade.getCorrectCount(), grade.getTotalCount()));
        } else {
            sb.append("\n\n").append(copy("telegram.bot.quiz.progress", locale,
                    grade.getAnsweredCount(), grade.getTotalCount()));
        }
        return sb.toString();
    }

    private void handleCallbackQuery(CallbackQuery callback) {
        if (callback == null) {
            return;
        }
        if (callback.getId() != null) {
            telegramApiClient.answerCallbackQuery(callback.getId());
        }
        MaybeInaccessibleMessage callbackMessage = callback.getMessage();
        Long chatId = callbackMessage == null || callbackMessage.getChat() == null
                ? null : callbackMessage.getChat().getId();
        if (chatId == null) {
            return;
        }
        Optional<UUID> linkedUser = telegramService.findLinkedUser(chatId);
        if (linkedUser.isEmpty()) {
            return;
        }
        UUID userId = linkedUser.get();
        long resolvedChatId = chatId;
        runAsUser(userId, () -> {
            SupportedLocale locale = localeOf(profilesApi.find(userId).orElse(null));
            telegramService.markSeen(resolvedChatId);
            String data = callback.getData();
            if (handleChatCallback(resolvedChatId, userId, locale, data)) {
                return;
            }
            String prompt = promptForAction(data, locale);
            if (prompt != null) {
                runCoachTurn(resolvedChatId, userId, locale, prompt);
            } else if (data != null && data.endsWith("upload_material")) {
                send(resolvedChatId, copy("telegram.bot.action.upload", locale));
            }
        });
    }

    private boolean handleChatCallback(long chatId, UUID userId, SupportedLocale locale, String data) {
        if (data == null) {
            return false;
        }
        if (data.equals(CHAT_NEW)) {
            handleNewChat(chatId, userId, locale);
            return true;
        }
        if (data.equals(CHAT_LIST)) {
            handleChats(chatId, userId, locale);
            return true;
        }
        if (data.startsWith(CHAT_SWITCH_PREFIX)) {
            handleSwitch(chatId, userId, locale, data.substring(CHAT_SWITCH_PREFIX.length()));
            return true;
        }
        if (data.equals(FORGET_CONFIRM)) {
            handleForget(chatId, userId, locale);
            return true;
        }
        return false;
    }

    private String promptForAction(String data, SupportedLocale locale) {
        if (data == null) {
            return null;
        }
        String action = data.startsWith(ACTION_PREFIX) ? data.substring(ACTION_PREFIX.length()) : data;
        return switch (action) {
            case "start_quiz" -> copy("telegram.bot.quizPrompt", locale);
            case "review_concept" -> copy("telegram.bot.action.reviewPrompt", locale);
            case "read_material" -> copy("telegram.bot.action.readPrompt", locale);
            default -> null;
        };
    }

    private List<InlineButton> buttonsFor(List<ActionPart> actions) {
        List<InlineButton> buttons = new ArrayList<>();
        for (ActionPart action : actions) {
            if (action.getAction() == null || action.getLabel() == null) {
                continue;
            }
            buttons.add(new InlineButton(action.getLabel(), ACTION_PREFIX + action.getAction()));
        }
        return buttons;
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return "";
        }
        String trimmed = value.strip();
        if (trimmed.length() <= max) {
            return trimmed;
        }
        return trimmed.substring(0, Math.max(0, max - 1)).strip() + "\u2026";
    }

    private String streakMessage(UUID userId, SupportedLocale locale) {
        StreakDto streak = streaksApi.getStreak(userId);
        if (streak == null || streak.getStreakCurrent() <= 0) {
            return copy("telegram.bot.streak.none", locale);
        }
        return copy("telegram.bot.streak.active", locale, streak.getStreakCurrent(), streak.getStreakLongest());
    }

    private String todayMessage(UUID userId, ProfileDto profile, SupportedLocale locale) {
        Optional<LearningPlanDto> active = planApi.getActivePlan(new CurrentUser(userId));
        if (active.isEmpty() || active.get().getSteps() == null || active.get().getSteps().isEmpty()) {
            return copy("telegram.bot.today.noPlan", locale);
        }
        List<PlanStepDto> steps = active.get().getSteps();
        LocalDate today = LocalDate.now(zoneOf(profile));
        Optional<PlanStepDto> scheduled = steps.stream()
                .filter(step -> !step.isDone() && today.equals(step.getScheduledDate()))
                .findFirst();
        if (scheduled.isPresent()) {
            return copy("telegram.bot.today.step", locale, escapeHtml(scheduled.get().getTitle()));
        }
        Optional<PlanStepDto> next = steps.stream()
                .filter(step -> !step.isDone())
                .min(Comparator.comparingInt(PlanStepDto::getDayIndex));
        if (next.isPresent()) {
            return copy("telegram.bot.today.next", locale, escapeHtml(next.get().getTitle()));
        }
        return copy("telegram.bot.today.allDone", locale);
    }

    private ZoneId zoneOf(ProfileDto profile) {
        String timezone = profile == null ? null : profile.getTimezone();
        if (timezone != null && !timezone.isBlank()) {
            try {
                return ZoneId.of(timezone);
            } catch (RuntimeException ignored) {
                return ZoneId.of(DEFAULT_TIMEZONE);
            }
        }
        return ZoneId.of(DEFAULT_TIMEZONE);
    }

    private SupportedLocale localeOf(ProfileDto profile) {
        if (profile == null || profile.getLocale() == null) {
            return SupportedLocale.DEFAULT;
        }
        return profile.getLocale();
    }

    private String copy(String key, SupportedLocale locale, Object... args) {
        return messageService.get(key, args.length == 0 ? null : args, locale.getLocale());
    }

    private String escapeHtml(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private void send(long chatId, String text) {
        telegramApiClient.sendMessage(chatId, text);
    }
}
