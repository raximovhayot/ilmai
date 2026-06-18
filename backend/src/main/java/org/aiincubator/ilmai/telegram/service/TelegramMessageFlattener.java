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

import java.util.ArrayList;
import java.util.List;

@Component
public class TelegramMessageFlattener {

    private static final int MAX_MESSAGE_LENGTH = 4096;
    private static final int MAX_BODY_LENGTH = 3500;
    private static final int MAX_SNIPPET_LENGTH = 200;
    private static final char[] OPTION_LETTERS = {'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h'};

    private final MessageService messageService;

    public TelegramMessageFlattener(MessageService messageService) {
        this.messageService = messageService;
    }

    public String flatten(List<MessagePart> parts, SupportedLocale locale) {
        if (parts == null || parts.isEmpty()) {
            return "";
        }
        SupportedLocale effective = locale == null ? SupportedLocale.DEFAULT : locale;

        List<TextPart> texts = new ArrayList<>();
        List<CitationPart> citations = new ArrayList<>();
        List<QuizCardPart> quizCards = new ArrayList<>();
        List<ErrorPart> errors = new ArrayList<>();
        for (MessagePart part : parts) {
            if (part instanceof TextPart textPart) {
                texts.add(textPart);
            } else if (part instanceof CitationPart citationPart) {
                citations.add(citationPart);
            } else if (part instanceof QuizCardPart quizCardPart) {
                quizCards.add(quizCardPart);
            } else if (part instanceof ErrorPart errorPart) {
                errors.add(errorPart);
            }
        }

        StringBuilder sb = new StringBuilder();
        String body = joinText(texts);
        boolean lowConfidence = texts.stream().anyMatch(t -> t.getConfidence() == TextConfidence.LOW);

        if (body.isBlank() && !errors.isEmpty()) {
            sb.append(escape(joinErrors(errors)));
        } else {
            if (lowConfidence) {
                sb.append("<i>")
                        .append(escape(label("telegram.bot.flatten.lowConfidence", effective)))
                        .append("</i>\n\n");
            }
            sb.append(escape(body));
        }

        if (!quizCards.isEmpty()) {
            sb.append("\n\n<b>")
                    .append(escape(label("telegram.bot.flatten.quiz", effective)))
                    .append("</b>");
            for (QuizCardPart card : quizCards) {
                sb.append("\n\n<b>").append(card.getPosition()).append(". </b>")
                        .append(escape(card.getPrompt()));
                List<String> options = card.getOptions();
                if (options != null) {
                    for (int i = 0; i < options.size(); i++) {
                        sb.append('\n');
                        if (i < OPTION_LETTERS.length) {
                            sb.append(OPTION_LETTERS[i]).append(") ");
                        }
                        sb.append(escape(options.get(i)));
                    }
                }
            }
        }

        if (!citations.isEmpty()) {
            sb.append("\n\n\uD83D\uDCDA <b>")
                    .append(escape(label("telegram.bot.flatten.sources", effective)))
                    .append("</b>");
            for (CitationPart citation : citations) {
                sb.append("\n\u2022 <i>").append(escape(citationText(citation))).append("</i>");
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

    private String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
