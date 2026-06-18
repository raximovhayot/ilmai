package org.aiincubator.ilmai.telegram.service;

import org.aiincubator.ilmai.agent.CitationPart;
import org.aiincubator.ilmai.agent.ErrorPart;
import org.aiincubator.ilmai.agent.MessagePart;
import org.aiincubator.ilmai.agent.QuizCardPart;
import org.aiincubator.ilmai.agent.TextConfidence;
import org.aiincubator.ilmai.agent.TextPart;
import org.aiincubator.ilmai.common.i18n.MessageService;
import org.aiincubator.ilmai.common.i18n.SupportedLocale;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class TelegramMessageFlattener {

    private static final int MAX_MESSAGE_LENGTH = 4096;
    private static final int MAX_BODY_LENGTH = 3500;
    private static final int MAX_SNIPPET_LENGTH = 200;
    private static final char[] OPTION_LETTERS = {'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h'};

    private final MessageService messageService;
    private final TelegramMarkdownRenderer markdownRenderer;

    public TelegramMessageFlattener(MessageService messageService, TelegramMarkdownRenderer markdownRenderer) {
        this.messageService = messageService;
        this.markdownRenderer = markdownRenderer;
    }

    public String flatten(List<MessagePart> parts, SupportedLocale locale) {
        if (parts == null || parts.isEmpty()) {
            return "";
        }
        SupportedLocale effective = locale == null ? SupportedLocale.DEFAULT : locale;

        GroupedMessageParts grouped = new GroupedMessageParts(parts);
        List<TextPart> texts = grouped.getTexts();
        List<CitationPart> citations = grouped.getCitations();
        List<QuizCardPart> quizCards = grouped.getQuizCards();
        List<ErrorPart> errors = grouped.getErrors();

        StringBuilder sb = new StringBuilder();
        String body = joinText(texts);
        boolean lowConfidence = texts.stream().anyMatch(t -> t.getConfidence() == TextConfidence.LOW);

        if (body.isBlank() && !errors.isEmpty()) {
            sb.append(markdownRenderer.escape(joinErrors(errors)));
        } else {
            if (lowConfidence) {
                sb.append("_")
                        .append(markdownRenderer.escape(label("telegram.bot.flatten.lowConfidence", effective)))
                        .append("_\n\n");
            }
            sb.append(markdownRenderer.render(body));
        }

        if (!quizCards.isEmpty()) {
            sb.append("\n\n*")
                    .append(markdownRenderer.escape(label("telegram.bot.flatten.quiz", effective)))
                    .append("*");
            for (QuizCardPart card : quizCards) {
                sb.append("\n\n*").append(markdownRenderer.escape(card.getPosition() + ".")).append("* ")
                        .append(markdownRenderer.escape(card.getPrompt()));
                List<String> options = card.getOptions();
                if (options != null) {
                    for (int i = 0; i < options.size(); i++) {
                        sb.append('\n');
                        if (i < OPTION_LETTERS.length) {
                            sb.append(markdownRenderer.escape(OPTION_LETTERS[i] + ")")).append(' ');
                        }
                        sb.append(markdownRenderer.escape(options.get(i)));
                    }
                }
            }
        }

        if (!citations.isEmpty()) {
            sb.append("\n\n\uD83D\uDCDA *")
                    .append(markdownRenderer.escape(label("telegram.bot.flatten.sources", effective)))
                    .append("*");
            for (CitationPart citation : citations) {
                sb.append("\n\u2022 _").append(markdownRenderer.escape(citationText(citation))).append("_");
            }
        }

        String out = sb.toString().strip();
        if (out.length() > MAX_MESSAGE_LENGTH) {
            out = trimDanglingEscape(out.substring(0, MAX_MESSAGE_LENGTH - 1).strip()) + "\u2026";
        }
        return out;
    }

    public String flattenRaw(List<MessagePart> parts, SupportedLocale locale) {
        if (parts == null || parts.isEmpty()) {
            return "";
        }
        SupportedLocale effective = locale == null ? SupportedLocale.DEFAULT : locale;

        GroupedMessageParts grouped = new GroupedMessageParts(parts);
        List<TextPart> texts = grouped.getTexts();
        List<CitationPart> citations = grouped.getCitations();
        List<QuizCardPart> quizCards = grouped.getQuizCards();
        List<ErrorPart> errors = grouped.getErrors();

        StringBuilder sb = new StringBuilder();
        String body = joinText(texts);
        boolean lowConfidence = texts.stream().anyMatch(t -> t.getConfidence() == TextConfidence.LOW);

        if (body.isBlank() && !errors.isEmpty()) {
            sb.append(joinErrors(errors));
        } else {
            if (lowConfidence) {
                sb.append("_").append(label("telegram.bot.flatten.lowConfidence", effective)).append("_\n\n");
            }
            sb.append(body);
        }

        if (!quizCards.isEmpty()) {
            sb.append("\n\n**").append(label("telegram.bot.flatten.quiz", effective)).append("**");
            for (QuizCardPart card : quizCards) {
                sb.append("\n\n**").append(card.getPosition()).append(".** ").append(card.getPrompt());
                List<String> options = card.getOptions();
                if (options != null) {
                    for (int i = 0; i < options.size(); i++) {
                        sb.append('\n');
                        if (i < OPTION_LETTERS.length) {
                            sb.append(OPTION_LETTERS[i]).append(") ");
                        }
                        sb.append(options.get(i));
                    }
                }
            }
        }

        if (!citations.isEmpty()) {
            sb.append("\n\n\uD83D\uDCDA **").append(label("telegram.bot.flatten.sources", effective)).append("**");
            for (CitationPart citation : citations) {
                sb.append("\n\u2022 _").append(citationText(citation)).append("_");
            }
        }

        String out = sb.toString().strip();
        if (out.length() > MAX_MESSAGE_LENGTH) {
            out = out.substring(0, MAX_MESSAGE_LENGTH - 1).strip() + "\u2026";
        }
        return out;
    }

    private String joinText(List<TextPart> texts) {
        StringBuilder sb = new StringBuilder();
        for (TextPart text : texts) {
            if (text.getText() == null || text.getText().isBlank()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append("\n\n");
            }
            sb.append(text.getText().strip());
        }
        String body = sb.toString();
        if (body.length() > MAX_BODY_LENGTH) {
            body = body.substring(0, MAX_BODY_LENGTH).strip() + "\u2026";
        }
        return body;
    }

    private String joinErrors(List<ErrorPart> errors) {
        StringBuilder sb = new StringBuilder();
        for (ErrorPart error : errors) {
            if (error.getMessage() == null || error.getMessage().isBlank()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append('\n');
            }
            sb.append(error.getMessage().strip());
        }
        return sb.toString();
    }

    private String citationText(CitationPart citation) {
        String snippet = citation.getSnippet();
        if (snippet == null || snippet.isBlank()) {
            snippet = citation.getLocator() == null ? "" : citation.getLocator();
        }
        snippet = snippet.strip();
        if (snippet.length() > MAX_SNIPPET_LENGTH) {
            snippet = snippet.substring(0, MAX_SNIPPET_LENGTH).strip() + "\u2026";
        }
        String materialName = citation.getMaterialName();
        if (materialName != null && !materialName.isBlank()) {
            return materialName.strip() + " \u2014 " + snippet;
        }
        return snippet;
    }

    private String label(String key, SupportedLocale locale) {
        return messageService.get(key, null, locale.getLocale());
    }

    private String trimDanglingEscape(String value) {
        int trailing = 0;
        for (int i = value.length() - 1; i >= 0 && value.charAt(i) == '\\'; i--) {
            trailing++;
        }
        if (trailing % 2 == 1) {
            return value.substring(0, value.length() - 1);
        }
        return value;
    }
}
