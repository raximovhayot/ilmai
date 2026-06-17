package org.aiincubator.ilmai.agent.service;

import org.aiincubator.ilmai.quiz.QuizCardDto;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class AgentQuizContext {

    private static final ThreadLocal<AgentQuizContext> CURRENT = new ThreadLocal<>();

    private final List<QuizCardDto> cards = new ArrayList<>();

    private AgentQuizContext() {
    }

    public static AgentQuizContext begin() {
        AgentQuizContext ctx = new AgentQuizContext();
        CURRENT.set(ctx);
        return ctx;
    }

    public static AgentQuizContext current() {
        return CURRENT.get();
    }

    public static void clear() {
        CURRENT.remove();
    }

    public void record(QuizCardDto card) {
        if (card != null) {
            cards.add(card);
        }
    }

    public List<QuizCardDto> cards() {
        return Collections.unmodifiableList(cards);
    }
}
