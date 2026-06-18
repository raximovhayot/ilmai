package org.aiincubator.ilmai.telegram.service;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class TelegramMarkdownRenderer {

    private static final Pattern SPECIAL = Pattern.compile("([_*\\[\\]()~`>#+\\-=|{}.!\\\\])");
    private static final Pattern FENCED_CODE =
            Pattern.compile("```[a-zA-Z0-9_+-]*\\r?\\n?(.*?)```", Pattern.DOTALL);
    private static final Pattern INLINE_CODE = Pattern.compile("`([^`\\n]+?)`");
    private static final Pattern LINK = Pattern.compile("\\[([^\\]]+?)]\\((https?://[^)\\s]+)\\)");
    private static final Pattern BOLD_STAR = Pattern.compile("\\*\\*(.+?)\\*\\*");
    private static final Pattern BOLD_UNDERSCORE = Pattern.compile("__(.+?)__");
    private static final Pattern STRIKE = Pattern.compile("~~(.+?)~~");
    private static final Pattern ITALIC_STAR = Pattern.compile("(?<![\\w*])\\*(?!\\s)(.+?)(?<!\\s)\\*(?![\\w*])");
    private static final Pattern ITALIC_UNDERSCORE = Pattern.compile("(?<![\\w_])_(?!\\s)(.+?)(?<!\\s)_(?![\\w_])");
    private static final Pattern HEADING = Pattern.compile("^\\s{0,3}#{1,6}\\s+(.*?)\\s*#*\\s*$");
    private static final Pattern BULLET = Pattern.compile("^(\\s*)[-*+]\\s+(.*)$");
    private static final Pattern BLOCKQUOTE = Pattern.compile("^\\s{0,3}>\\s?(.*)$");

    private static final String BOLD = "\u0000B\u0000";
    private static final String ITALIC = "\u0000I\u0000";
    private static final String STRIKE_MARK = "\u0000Z\u0000";
    private static final String BULLET_MARK = "\u0000U\u0000";
    private static final String QUOTE_MARK = "\u0000Q\u0000";

    public String escape(String value) {
        if (value == null) {
            return "";
        }
        return SPECIAL.matcher(value).replaceAll("\\\\$1");
    }

    public String render(String markdown) {
        if (markdown == null || markdown.isEmpty()) {
            return "";
        }
        List<String> tokens = new ArrayList<>();
        String working = protectFencedCode(markdown, tokens);
        working = protectInlineCode(working, tokens);
        working = processBlocks(working);
        working = protectLinks(working, tokens);
        working = processInline(working);
        working = escape(working);
        working = restoreMarkers(working);
        working = restoreTokens(working, tokens);
        return working.strip();
    }

    private String protectFencedCode(String input, List<String> tokens) {
        Matcher matcher = FENCED_CODE.matcher(input);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String content = matcher.group(1).replaceAll("\\r?\\n$", "");
            tokens.add("```\n" + escapeCode(content) + "\n```");
            matcher.appendReplacement(sb, Matcher.quoteReplacement(token(tokens.size() - 1)));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private String protectInlineCode(String input, List<String> tokens) {
        Matcher matcher = INLINE_CODE.matcher(input);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            tokens.add("`" + escapeCode(matcher.group(1)) + "`");
            matcher.appendReplacement(sb, Matcher.quoteReplacement(token(tokens.size() - 1)));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private String protectLinks(String input, List<String> tokens) {
        Matcher matcher = LINK.matcher(input);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            tokens.add("[" + escape(matcher.group(1)) + "](" + escapeUrl(matcher.group(2)) + ")");
            matcher.appendReplacement(sb, Matcher.quoteReplacement(token(tokens.size() - 1)));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private String processBlocks(String input) {
        String[] lines = input.split("\n", -1);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            if (i > 0) {
                sb.append('\n');
            }
            sb.append(processBlockLine(lines[i]));
        }
        return sb.toString();
    }

    private String processBlockLine(String line) {
        Matcher heading = HEADING.matcher(line);
        if (heading.matches()) {
            String text = heading.group(1).strip();
            return text.isEmpty() ? "" : BOLD + text + BOLD;
        }
        Matcher bullet = BULLET.matcher(line);
        if (bullet.matches()) {
            return bullet.group(1) + BULLET_MARK + " " + bullet.group(2);
        }
        Matcher quote = BLOCKQUOTE.matcher(line);
        if (quote.matches()) {
            return QUOTE_MARK + quote.group(1);
        }
        return line;
    }

    private String processInline(String input) {
        String out = BOLD_STAR.matcher(input).replaceAll(BOLD + "$1" + BOLD);
        out = BOLD_UNDERSCORE.matcher(out).replaceAll(BOLD + "$1" + BOLD);
        out = STRIKE.matcher(out).replaceAll(STRIKE_MARK + "$1" + STRIKE_MARK);
        out = ITALIC_STAR.matcher(out).replaceAll(ITALIC + "$1" + ITALIC);
        out = ITALIC_UNDERSCORE.matcher(out).replaceAll(ITALIC + "$1" + ITALIC);
        return out;
    }

    private String restoreMarkers(String input) {
        return input
                .replace(BOLD, "*")
                .replace(ITALIC, "_")
                .replace(STRIKE_MARK, "~")
                .replace(BULLET_MARK, "\u2022")
                .replace(QUOTE_MARK, ">");
    }

    private String restoreTokens(String input, List<String> tokens) {
        String out = input;
        for (int i = 0; i < tokens.size(); i++) {
            out = out.replace(token(i), tokens.get(i));
        }
        return out;
    }

    private String token(int index) {
        return "\u0000T" + index + "\u0000";
    }

    private String escapeCode(String value) {
        return value.replace("\\", "\\\\").replace("`", "\\`");
    }

    private String escapeUrl(String value) {
        return value.replace("\\", "\\\\").replace(")", "\\)");
    }
}
