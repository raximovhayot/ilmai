package org.aiincubator.ilmai.telegram.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aiincubator.ilmai.telegram.config.TelegramProperties;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.GetFile;
import org.telegram.telegrambots.meta.api.methods.polls.SendPoll;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendMessageDraft;
import org.telegram.telegrambots.meta.api.methods.updates.SetWebhook;
import org.telegram.telegrambots.meta.api.objects.File;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.api.objects.polls.input.InputPollOption;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class TelegramApiClient {

    private static final String PARSE_MODE_HTML = "HTML";
    private static final int EXPLANATION_MAX = 200;
    private static final int DRAFT_TEXT_MAX = 4096;

    private final TelegramClient telegramClient;
    private final TelegramProperties properties;

    public boolean isEnabled() {
        return properties.getBotToken() != null && !properties.getBotToken().isBlank();
    }

    public boolean registerWebhook() {
        if (!isEnabled()) {
            log.info("telegram disabled — skipping webhook registration");
            return false;
        }
        String baseUrl = properties.getPublicBaseUrl();
        if (baseUrl == null || baseUrl.isBlank()) {
            log.warn("telegram public-base-url is not set — skipping webhook registration");
            return false;
        }
        String secret = properties.getWebhookSecret();
        if (secret == null || secret.isBlank()) {
            log.warn("telegram webhook-secret is not set — skipping webhook registration");
            return false;
        }
        String webhookUrl = baseUrl.replaceAll("/+$", "") + "/telegram/webhook/" + secret;
        SetWebhook request = SetWebhook.builder()
                .url(webhookUrl)
                .secretToken(secret)
                .build();
        try {
            Boolean result = telegramClient.execute(request);
            boolean ok = Boolean.TRUE.equals(result);
            if (ok) {
                log.info("telegram webhook registered at {}", webhookUrl);
            } else {
                log.warn("telegram setWebhook returned false for {}", webhookUrl);
            }
            return ok;
        } catch (TelegramApiException | RuntimeException ex) {
            log.warn("telegram registerWebhook failed (url={}): {}", webhookUrl, ex.toString());
            return false;
        }
    }

    public boolean sendMessage(Long chatId, String text) {
        if (!isEnabled() || chatId == null || text == null || text.isBlank()) {
            log.debug("telegram disabled — skipping sendMessage to chat={}", chatId);
            return false;
        }
        SendMessage request = SendMessage.builder()
                .chatId(String.valueOf(chatId))
                .text(text)
                .parseMode(PARSE_MODE_HTML)
                .build();
        try {
            telegramClient.execute(request);
            return true;
        } catch (TelegramApiException | RuntimeException ex) {
            log.warn("telegram sendMessage failed (chat={}): {}", chatId, ex.toString());
            return false;
        }
    }

    public boolean streamMessage(Long chatId, int draftId, String partialText) {
        if (!isEnabled() || chatId == null || draftId == 0) {
            return false;
        }
        String text = partialText == null ? "" : partialText;
        if (text.length() > DRAFT_TEXT_MAX) {
            text = text.substring(0, DRAFT_TEXT_MAX);
        }
        SendMessageDraft request = SendMessageDraft.builder()
                .chatId(chatId)
                .draftId(draftId)
                .text(text)
                .build();
        try {
            telegramClient.execute(request);
            return true;
        } catch (TelegramApiException | RuntimeException ex) {
            log.warn("telegram streamMessage failed (chat={}): {}", chatId, ex.toString());
            return false;
        }
    }

    public boolean sendMessage(Long chatId, String text, List<InlineButton> buttons) {
        if (buttons == null || buttons.isEmpty()) {
            return sendMessage(chatId, text);
        }
        if (!isEnabled() || chatId == null || text == null || text.isBlank()) {
            log.debug("telegram disabled — skipping sendMessage(keyboard) to chat={}", chatId);
            return false;
        }
        List<InlineKeyboardRow> keyboard = new ArrayList<>();
        for (InlineButton button : buttons) {
            keyboard.add(new InlineKeyboardRow(InlineKeyboardButton.builder()
                    .text(button.getText())
                    .callbackData(button.getCallbackData())
                    .build()));
        }
        SendMessage request = SendMessage.builder()
                .chatId(String.valueOf(chatId))
                .text(text)
                .parseMode(PARSE_MODE_HTML)
                .replyMarkup(InlineKeyboardMarkup.builder().keyboard(keyboard).build())
                .build();
        try {
            telegramClient.execute(request);
            return true;
        } catch (TelegramApiException | RuntimeException ex) {
            log.warn("telegram sendMessage(keyboard) failed (chat={}): {}", chatId, ex.toString());
            return false;
        }
    }

    public String sendPoll(Long chatId, String question, List<String> options) {
        if (!isEnabled() || chatId == null || question == null || question.isBlank()
                || options == null || options.size() < 2) {
            log.debug("telegram disabled or invalid poll — skipping sendPoll to chat={}", chatId);
            return null;
        }
        List<InputPollOption> pollOptions = new ArrayList<>();
        for (String option : options) {
            pollOptions.add(InputPollOption.builder().text(option).build());
        }
        SendPoll request = SendPoll.builder()
                .chatId(String.valueOf(chatId))
                .question(question)
                .options(pollOptions)
                .type("regular")
                .isAnonymous(false)
                .allowMultipleAnswers(false)
                .build();
        try {
            Message message = telegramClient.execute(request);
            return extractPollId(message);
        } catch (TelegramApiException | RuntimeException ex) {
            log.warn("telegram sendPoll failed (chat={}): {}", chatId, ex.toString());
            return null;
        }
    }

    public String sendQuizPoll(Long chatId, String question, List<String> options,
                               int correctOptionId, String explanation) {
        if (!isEnabled() || chatId == null || question == null || question.isBlank()
                || options == null || options.size() < 2
                || correctOptionId < 0 || correctOptionId >= options.size()) {
            log.debug("telegram disabled or invalid quiz poll — skipping sendQuizPoll to chat={}", chatId);
            return null;
        }
        List<InputPollOption> pollOptions = new ArrayList<>();
        for (String option : options) {
            pollOptions.add(InputPollOption.builder().text(option).build());
        }
        SendPoll.SendPollBuilder<?, ?> builder = SendPoll.builder()
                .chatId(String.valueOf(chatId))
                .question(question)
                .options(pollOptions)
                .type("quiz")
                .correctOptionIds(List.of(correctOptionId))
                .isAnonymous(false)
                .allowMultipleAnswers(false);
        if (explanation != null && !explanation.isBlank()) {
            builder.explanation(truncate(explanation, EXPLANATION_MAX));
        }
        try {
            Message message = telegramClient.execute(builder.build());
            return extractPollId(message);
        } catch (TelegramApiException | RuntimeException ex) {
            log.warn("telegram sendQuizPoll failed (chat={}): {}", chatId, ex.toString());
            return null;
        }
    }

    public File getFile(String fileId) {
        if (!isEnabled() || fileId == null || fileId.isBlank()) {
            return null;
        }
        try {
            return telegramClient.execute(GetFile.builder().fileId(fileId).build());
        } catch (TelegramApiException | RuntimeException ex) {
            log.warn("telegram getFile failed (fileId={}): {}", fileId, ex.toString());
            return null;
        }
    }

    public byte[] downloadFile(File file) {
        if (!isEnabled() || file == null || file.getFilePath() == null || file.getFilePath().isBlank()) {
            return null;
        }
        try (InputStream in = telegramClient.downloadFileAsStream(file)) {
            return in.readAllBytes();
        } catch (TelegramApiException | IOException | RuntimeException ex) {
            log.warn("telegram downloadFile failed (path={}): {}", file.getFilePath(), ex.toString());
            return null;
        }
    }

    public void answerCallbackQuery(String callbackQueryId) {
        if (!isEnabled() || callbackQueryId == null || callbackQueryId.isBlank()) {
            return;
        }
        AnswerCallbackQuery request = AnswerCallbackQuery.builder()
                .callbackQueryId(callbackQueryId)
                .build();
        try {
            telegramClient.execute(request);
        } catch (TelegramApiException | RuntimeException ex) {
            log.warn("telegram answerCallbackQuery failed (id={}): {}", callbackQueryId, ex.toString());
        }
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
    }

    private String extractPollId(Message message) {
        if (message == null || message.getPoll() == null) {
            return null;
        }
        return message.getPoll().getId();
    }
}
