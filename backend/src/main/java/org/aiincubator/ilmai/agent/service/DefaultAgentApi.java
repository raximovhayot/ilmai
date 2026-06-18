package org.aiincubator.ilmai.agent.service;

import lombok.extern.slf4j.Slf4j;
import org.aiincubator.ilmai.agent.ActionPart;
import org.aiincubator.ilmai.agent.AgentApi;
import org.aiincubator.ilmai.agent.AgentErrorCodes;
import org.aiincubator.ilmai.agent.ChatChannel;
import org.aiincubator.ilmai.agent.CitationPart;
import org.aiincubator.ilmai.agent.ErrorPart;
import org.aiincubator.ilmai.agent.MessagePart;
import org.aiincubator.ilmai.agent.QuizCardPart;
import org.aiincubator.ilmai.agent.RetrievedChunk;
import org.aiincubator.ilmai.agent.TextConfidence;
import org.aiincubator.ilmai.agent.TextPart;
import org.aiincubator.ilmai.common.CurrentUser;
import org.aiincubator.ilmai.common.i18n.MessageService;
import org.aiincubator.ilmai.common.quota.IlmTokenReservation;
import org.aiincubator.ilmai.quiz.QuizCardDto;
import org.aiincubator.ilmai.quiz.QuizCardQuestionDto;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
public class DefaultAgentApi implements AgentApi {

    private final ObjectProvider<ChatClient> coachChatClientProvider;
    private final MessageService messageService;
    private final ChatSessionService chatSessionService;
    private final CoachTurnSupport turnSupport;

    public DefaultAgentApi(
            @Qualifier(CoachChatClientConfig.COACH_CHAT_CLIENT) ObjectProvider<ChatClient> coachChatClientProvider,
            MessageService messageService,
            ChatSessionService chatSessionService,
            CoachTurnSupport turnSupport) {
        this.coachChatClientProvider = coachChatClientProvider;
        this.messageService = messageService;
        this.chatSessionService = chatSessionService;
        this.turnSupport = turnSupport;
    }

    @Override
    public UUID canonicalSession(CurrentUser currentUser, ChatChannel channel) {
        if (currentUser == null) {
            throw new IllegalArgumentException("currentUser is required");
        }
        return chatSessionService.getOrCreateCanonical(currentUser, channel);
    }

    @Override
    public Flux<MessagePart> chat(CurrentUser currentUser, UUID sessionId, String prompt, ChatChannel channel) {
        if (currentUser == null) {
            throw new IllegalArgumentException("currentUser is required");
        }
        if (sessionId == null) {
            throw new IllegalArgumentException("sessionId is required");
        }
        chatSessionService.requireOwnedSession(currentUser, sessionId);
        if (!turnSupport.canSpend(currentUser)) {
            log.debug("agent.chat quota-exceeded user={} session={} estimate={}",
                    currentUser.getUserId(), sessionId, CoachTurnSupport.PER_TURN_ESTIMATE_ILM_TOKENS);
            return Flux.just(new ErrorPart(
                    AgentErrorCodes.QUOTA_EXCEEDED,
                    messageService.get(CoachTurnSupport.QUOTA_EXCEEDED_MESSAGE_KEY),
                    false));
        }
        ChatClient client = coachChatClientProvider.getIfAvailable();
        if (client == null) {
            log.warn("agent.chat: no Coach ChatClient available (no LLM provider configured) user={} session={}",
                    currentUser.getUserId(), sessionId);
            return Flux.just(new TextPart("Coach is not configured."));
        }
        log.debug("agent.chat user={} session={} channel={} promptLen={}",
                currentUser.getUserId(), sessionId, channel,
                prompt == null ? 0 : prompt.length());

        IlmTokenReservation reservation = turnSupport.reserve(currentUser);
        AgentRetrievalContext ctx = AgentRetrievalContext.begin();
        AgentResponseFlags flags = AgentResponseFlags.begin();
        AgentQuizContext quizCtx = AgentQuizContext.begin();
        AgentActionContext actionCtx = AgentActionContext.begin();
        boolean committed = false;
        try {
            String userMessage = prompt == null ? "" : prompt;
            ChatResponse response = client.prompt()
                    .user(userMessage)
                    .advisors(advisor -> advisor
                            .param(ChatMemory.CONVERSATION_ID, sessionId.toString())
                            .param(UserMemoryAdvisor.CURRENT_USER_PARAM, currentUser))
                    .tools(t -> t.context(Map.of(AgentToolContext.CURRENT_USER_KEY, currentUser)))
                    .call()
                    .chatResponse();
            String text = extractText(response);
            int actualIlmTokens = turnSupport.commit(reservation, response);
            committed = true;
            log.debug("agent.chat committed user={} session={} actualIlmTokens={}",
                    currentUser.getUserId(), sessionId, actualIlmTokens);
            turnSupport.completeTurnQuietly(currentUser, sessionId);

            List<MessagePart> parts = new ArrayList<>();
            for (RetrievedChunk chunk : ctx.chunks()) {
                parts.add(new CitationPart(
                        UUID.randomUUID(),
                        chunk.getMaterialId(),
                        chunk.getMaterialName(),
                        chunk.getChunkIndex() == null ? null : "t" + chunk.getChunkIndex(),
                        chunk.getSnippet(),
                        chunk.getScore() == null ? 0.0 : chunk.getScore()));
            }
            TextConfidence confidence = flags.isLowConfidence() ? TextConfidence.LOW : TextConfidence.HIGH;
            parts.add(new TextPart(text, confidence));
            for (QuizCardDto card : quizCtx.cards()) {
                for (QuizCardQuestionDto question : card.getQuestions()) {
                    parts.add(toQuizCardPart(card, question));
                }
            }
            for (ActionPart action : actionCtx.actions()) {
                parts.add(action);
            }
            return Flux.fromIterable(parts);
        } finally {
            if (!committed) {
                turnSupport.refund(reservation);
                log.debug("agent.chat refunded user={} session={} estimate={}",
                        currentUser.getUserId(), sessionId, CoachTurnSupport.PER_TURN_ESTIMATE_ILM_TOKENS);
            }
            AgentResponseFlags.clear();
            AgentRetrievalContext.clear();
            AgentQuizContext.clear();
            AgentActionContext.clear();
        }
    }

    private QuizCardPart toQuizCardPart(QuizCardDto card, QuizCardQuestionDto question) {
        return new QuizCardPart(
                card.getSessionId(),
                question.getQuestionId(),
                question.getPosition(),
                question.getType(),
                question.getConcept(),
                question.getPrompt(),
                question.getOptions(),
                question.getMaterialId(),
                question.getMaterialName(),
                question.getChunkIndex());
    }

    private String extractText(ChatResponse response) {
        if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
            return "";
        }
        String text = response.getResult().getOutput().getText();
        return text == null ? "" : text;
    }
}
