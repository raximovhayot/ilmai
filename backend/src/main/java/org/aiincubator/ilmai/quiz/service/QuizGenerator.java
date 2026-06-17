package org.aiincubator.ilmai.quiz.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aiincubator.ilmai.ai.IlmaiChatClientFactory;
import org.aiincubator.ilmai.ai.RetrievedChunkDto;
import org.aiincubator.ilmai.common.i18n.SupportedLocale;
import org.aiincubator.ilmai.quiz.domain.QuestionType;
import org.aiincubator.ilmai.quiz.domain.QuizDifficulty;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
@Slf4j
public class QuizGenerator {

    private static final Pattern JSON_ARRAY = Pattern.compile("\\[\\s*\\{.*}\\s*]", Pattern.DOTALL);

    private final IlmaiChatClientFactory chatClientFactory;
    private final JsonMapper jsonMapper;

    public List<QuestionDraft> generate(QuizDifficulty difficulty,
                                        SupportedLocale locale,
                                        int desired,
                                        List<RetrievedChunkDto> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return List.of();
        }
        ChatClient.Builder builder = chatClientFactory.builder();
        if (builder == null) {
            return fallback(chunks, desired);
        }
        ChatClient client = builder.build();
        String userPrompt = buildPrompt(difficulty, locale, desired, chunks);
        try {
            String response = client.prompt(new Prompt(userPrompt)).call().content();
            return parseDrafts(response, chunks);
        } catch (RuntimeException ex) {
            log.warn("quiz generation failed: {}", ex.toString());
            return fallback(chunks, desired);
        }
    }

    private String buildPrompt(QuizDifficulty difficulty, SupportedLocale locale, int desired, List<RetrievedChunkDto> chunks) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are a tutor. Generate exactly ").append(desired)
                .append(" learning questions in language ").append(localeCode(locale))
                .append(" at difficulty ").append(difficulty.name().toLowerCase(Locale.ROOT))
                .append(".\nReply with ONLY a JSON array (no prose, no markdown) where each element has the keys:\n")
                .append("  type        — one of MULTIPLE_CHOICE, SHORT_ANSWER, OPEN_ENDED\n")
                .append("  concept     — the single concept tested (string, ≤120 chars)\n")
                .append("  prompt      — the question text\n")
                .append("  options     — for MULTIPLE_CHOICE: 4 strings; otherwise []\n")
                .append("  correctAnswer — exact answer string (or empty for OPEN_ENDED)\n")
                .append("  explanation — brief explanation in the same language\n")
                .append("  materialId  — UUID copied verbatim from the source chunk header\n")
                .append("  chunkIndex  — integer copied from the source chunk header\n")
                .append("Use ONLY the following uploaded chunks; never invent material outside them.\n\n");
        for (RetrievedChunkDto c : chunks) {
            sb.append("--- chunk\n");
            sb.append("materialId=").append(c.getMaterialId() == null ? "unknown" : c.getMaterialId()).append("\n");
            sb.append("chunkIndex=").append(c.getChunkIndex() == null ? 0 : c.getChunkIndex()).append("\n");
            sb.append("materialName=").append(c.getMaterialName() == null ? "" : c.getMaterialName()).append("\n");
            sb.append("content=").append(c.getContent() == null ? "" : c.getContent()).append("\n");
        }
        return sb.toString();
    }

    private List<QuestionDraft> parseDrafts(String response, List<RetrievedChunkDto> chunks) {
        if (response == null) {
            return fallback(chunks, chunks.size());
        }
        Matcher m = JSON_ARRAY.matcher(response);
        String json = m.find() ? m.group() : response.trim();
        try {
            List<Map<String, Object>> raw = jsonMapper.readValue(json, new TypeReference<List<Map<String, Object>>>() {
            });
            List<QuestionDraft> drafts = new ArrayList<>(raw.size());
            for (Map<String, Object> item : raw) {
                QuestionType type = parseType(item.get("type"));
                String prompt = stringOrEmpty(item.get("prompt"));
                if (prompt.isBlank()) {
                    continue;
                }
                List<String> options = parseOptions(item.get("options"));
                String correct = stringOrEmpty(item.get("correctAnswer"));
                String explanation = stringOrEmpty(item.get("explanation"));
                String concept = stringOrEmpty(item.get("concept"));
                UUID materialId = parseUuid(item.get("materialId"));
                Integer chunkIndex = parseInt(item.get("chunkIndex"));
                String materialName = chunks.stream()
                        .filter(c -> c.getMaterialId() != null && c.getMaterialId().equals(materialId))
                        .map(RetrievedChunkDto::getMaterialName)
                        .findFirst()
                        .orElse(null);
                drafts.add(new QuestionDraft(type, concept, prompt, options, correct, explanation, materialId, materialName, chunkIndex));
            }
            return drafts;
        } catch (Exception ex) {
            log.warn("quiz JSON parsing failed: {}", ex.toString());
            return fallback(chunks, chunks.size());
        }
    }

    private List<QuestionDraft> fallback(List<RetrievedChunkDto> chunks, int desired) {
        List<QuestionDraft> result = new ArrayList<>();
        int limit = Math.min(desired, chunks.size());
        for (int i = 0; i < limit; i++) {
            RetrievedChunkDto c = chunks.get(i);
            String body = c.getContent() == null ? "" : c.getContent();
            String snippet = body.length() <= 300 ? body : body.substring(0, 300);
            String prompt = "What is the main idea of this passage?\n\n" + snippet;
            result.add(new QuestionDraft(
                    QuestionType.OPEN_ENDED,
                    null,
                    prompt,
                    List.of(),
                    "",
                    "See the cited material chunk for context.",
                    c.getMaterialId(),
                    c.getMaterialName(),
                    c.getChunkIndex()
            ));
        }
        return result;
    }

    private QuestionType parseType(Object value) {
        if (value == null) {
            return QuestionType.OPEN_ENDED;
        }
        try {
            return QuestionType.valueOf(value.toString().trim().toUpperCase(Locale.ROOT).replace('-', '_'));
        } catch (IllegalArgumentException ex) {
            return QuestionType.OPEN_ENDED;
        }
    }

    @SuppressWarnings("unchecked")
    private List<String> parseOptions(Object value) {
        if (value instanceof List<?> list) {
            List<String> out = new ArrayList<>(list.size());
            for (Object o : list) {
                if (o != null) {
                    out.add(o.toString());
                }
            }
            return out;
        }
        if (value instanceof String s && !s.isBlank()) {
            return Arrays.stream(s.split("\\|")).map(String::trim).filter(p -> !p.isEmpty()).toList();
        }
        return List.of();
    }

    private String stringOrEmpty(Object value) {
        return value == null ? "" : value.toString();
    }

    private UUID parseUuid(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return UUID.fromString(value.toString());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private Integer parseInt(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String localeCode(SupportedLocale locale) {
        return locale == null ? "EN" : locale.name();
    }
}
