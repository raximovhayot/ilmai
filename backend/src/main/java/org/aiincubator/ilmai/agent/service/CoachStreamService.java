package org.aiincubator.ilmai.agent.service;

import lombok.extern.slf4j.Slf4j;
import org.aiincubator.ilmai.agent.ChatChannel;
import org.aiincubator.ilmai.common.CurrentUser;
import org.aiincubator.ilmai.common.i18n.MessageService;
import org.aiincubator.ilmai.common.quota.IlmTokenReservation;
import org.aiincubator.ilmai.rooms.RoomsApi;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;
import uz.uzinfoweb.uimessagestream.core.UiMessageStreamWriter;
import uz.uzinfoweb.uimessagestream.spring.ChatClientResponseMapper;
import uz.uzinfoweb.uimessagestream.spring.SerializedPartSink;
import uz.uzinfoweb.uimessagestream.spring.UiMessageStreamAdvisor;
import uz.uzinfoweb.uimessagestream.spring.UiMessageStreamEmitter;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

@Service
@Slf4j
public class CoachStreamService {

    private final ObjectProvider<ChatClient> coachChatClientProvider;
    private final ChatSessionService chatSessionService;
    private final CoachTurnSupport turnSupport;
    private final MessageService messageService;
    private final UiMessageStreamEmitter transport;
    private final Executor coachStreamExecutor;
    private final ChatTranscriptService chatTranscriptService;
    private final RoomsApi roomsApi;

    public CoachStreamService(
            @Qualifier(CoachChatClientConfig.COACH_CHAT_CLIENT) ObjectProvider<ChatClient> coachChatClientProvider,
            ChatSessionService chatSessionService,
            CoachTurnSupport turnSupport,
            MessageService messageService,
            UiMessageStreamEmitter transport,
            @Qualifier(CoachChatClientConfig.COACH_STREAM_EXECUTOR) Executor coachStreamExecutor,
            ChatTranscriptService chatTranscriptService,
            RoomsApi roomsApi) {
        this.coachChatClientProvider = coachChatClientProvider;
        this.chatSessionService = chatSessionService;
        this.turnSupport = turnSupport;
        this.messageService = messageService;
        this.transport = transport;
        this.coachStreamExecutor = coachStreamExecutor;
        this.chatTranscriptService = chatTranscriptService;
        this.roomsApi = roomsApi;
    }

    public SseEmitter stream(CurrentUser currentUser, UUID sessionId, String prompt, ChatChannel channel) {
        return stream(currentUser, sessionId, prompt, null, channel);
    }

    public SseEmitter stream(CurrentUser currentUser, UUID sessionId, String prompt, String context, ChatChannel channel) {
        if (currentUser == null) {
            throw new IllegalArgumentException("currentUser is required");
        }
        if (sessionId == null) {
            throw new IllegalArgumentException("sessionId is required");
        }
        log.debug("agent.stream user={} session={} channel={} promptLen={}",
                currentUser.getUserId(), sessionId, channel, prompt == null ? 0 : prompt.length());
        UUID roomId = chatSessionService.requireOwnedSessionRoomId(currentUser, sessionId);
        roomsApi.requireMember(currentUser, roomId);
        if (!turnSupport.canSpend(currentUser)) {
            log.debug("agent.stream quota-exceeded user={} session={} estimate={}",
                    currentUser.getUserId(), sessionId, CoachTurnSupport.PER_TURN_ESTIMATE_ILM_TOKENS);
            String message = messageService.get(CoachTurnSupport.QUOTA_EXCEEDED_MESSAGE_KEY);
            return produce(writer -> {
                writer.start(UUID.randomUUID().toString());
                writer.error(message);
            });
        }
        ChatClient client = coachChatClientProvider.getIfAvailable();
        if (client == null) {
            log.warn("agent.stream: no Coach ChatClient available (no LLM provider configured) user={} session={}",
                    currentUser.getUserId(), sessionId);
            return produce(writer -> {
                writer.start(UUID.randomUUID().toString());
                writer.text("Coach is not configured.");
            });
        }
        return streamTurn(client, currentUser, sessionId, roomId, prompt == null ? "" : prompt, context);
    }

    private SseEmitter streamTurn(ChatClient client, CurrentUser currentUser, UUID sessionId, UUID roomId,
                                  String userMessage, String context) {
        boolean topicScoped = context != null && !context.isBlank();
        IlmTokenReservation reservation = turnSupport.reserve(currentUser);
        recordUserTurnQuietly(currentUser, sessionId, userMessage);
        String modelMessage = withContext(userMessage, context);
        SerializedPartSink sink = new SerializedPartSink();
        AgentRetrievalContext retrievalCtx = AgentRetrievalContext.create();
        ChatClientResponse turnCompleted = ChatClientResponse.builder().context(Map.of()).build();
        StringBuilder aggregatedText = new StringBuilder();
        AtomicReference<ChatResponse> lastChatResponse = new AtomicReference<>();
        AtomicBoolean committed = new AtomicBoolean(false);

        Flux<ChatClientResponse> upstream = client.prompt()
                .user(modelMessage)
                .advisors(advisor -> advisor
                        .param(ChatMemory.CONVERSATION_ID, sessionId.toString())
                        .param(UserMemoryAdvisor.CURRENT_USER_PARAM, currentUser)
                        .advisors(new UiMessageStreamAdvisor(sink, Ordered.HIGHEST_PRECEDENCE + 100)))
                .tools(t -> t.context(Map.of(
                        AgentToolContext.CURRENT_USER_KEY, currentUser,
                        AgentToolContext.ROOM_ID_KEY, roomId,
                        AgentToolContext.RETRIEVAL_CONTEXT_KEY, retrievalCtx)))
                .stream()
                .chatClientResponse()
                .concatWith(Flux.just(turnCompleted));

        SseEmitter emitter = new SseEmitter(0L);
        coachStreamExecutor.execute(() -> {
            try {
                transport.writeTo(emitter, upstream, ChatClientResponseMapper.TEXT_ONLY, sink,
                        response -> {
                            if (response == turnCompleted) {
                                finishTurn(currentUser, sessionId, sink, retrievalCtx,
                                        aggregatedText.toString(), lastChatResponse.get(), reservation,
                                        topicScoped);
                                committed.set(true);
                            } else {
                                accumulate(response, aggregatedText, lastChatResponse);
                            }
                        },
                        failure -> {
                            if (!committed.get()) {
                                turnSupport.refund(reservation);
                                log.debug("agent.stream refunded user={} session={} estimate={}",
                                        currentUser.getUserId(), sessionId,
                                        CoachTurnSupport.PER_TURN_ESTIMATE_ILM_TOKENS);
                            }
                            if (failure != null) {
                                if (isClientDisconnect(failure)) {
                                    log.debug("agent.stream client disconnected user={} session={}: {}",
                                            currentUser.getUserId(), sessionId, failure.getMessage());
                                } else {
                                    log.error("Error executing coach turn for user={} session={}",
                                            currentUser.getUserId(), sessionId, failure);
                                }
                            }
                        });
            } catch (Exception ex) {
                log.error("Failed to write to SSE emitter for user={} session={}",
                        currentUser.getUserId(), sessionId, ex);
            }
        });
        return emitter;
    }

    private void finishTurn(CurrentUser currentUser, UUID sessionId, SerializedPartSink sink,
                            AgentRetrievalContext retrievalCtx, String aggregatedText,
                            ChatResponse lastChatResponse, IlmTokenReservation reservation,
                            boolean topicScoped) {
        boolean grounded = retrievalCtx.hasGrounding();
        boolean cited = CitationGuardAdvisor.containsCitation(aggregatedText);
        boolean lowConfidence = !topicScoped && (!grounded || !cited);
        if (lowConfidence) {
            log.debug("agent.stream low-confidence user={} session={} grounded={} cited={}",
                    currentUser.getUserId(), sessionId, grounded, cited);
            sink.data("confidence", Map.of("level", "low"));
        }
        int actualIlmTokens = turnSupport.commit(reservation, lastChatResponse);
        log.debug("agent.stream committed user={} session={} actualIlmTokens={}",
                currentUser.getUserId(), sessionId, actualIlmTokens);
        turnSupport.completeTurnQuietly(currentUser, sessionId);
        recordAssistantTurnQuietly(currentUser, sessionId, aggregatedText, retrievalCtx, lowConfidence);
    }

    private String withContext(String userMessage, String context) {
        if (context == null || context.isBlank()) {
            return userMessage;
        }
        return """
                You are tutoring the learner on the specific topic below. Treat this lesson context as your \
                primary, authoritative source for this turn and answer the learner's question about it directly. \
                You may also explain concepts, terms, or references that this topic names even when they are not \
                in the learner's uploaded materials — answer those from your own general knowledge, but clearly \
                label such parts as general knowledge that is not drawn from their uploaded sources. When you do \
                draw on the learner's uploaded materials, still prefer the retrieve tool and cite it inline.

                <lesson_context>
                %s
                </lesson_context>

                Learner message:
                %s""".formatted(context.strip(), userMessage);
    }

    private void recordUserTurnQuietly(CurrentUser currentUser, UUID sessionId, String userMessage) {
        try {
            chatTranscriptService.recordUserTurn(currentUser, sessionId, userMessage);
        } catch (RuntimeException ex) {
            log.warn("agent.stream user-transcript persistence failed user={} session={}: {}",
                    currentUser.getUserId(), sessionId, ex.toString());
        }
    }

    private void recordAssistantTurnQuietly(CurrentUser currentUser, UUID sessionId, String assistantText,
                                            AgentRetrievalContext retrievalCtx, boolean lowConfidence) {
        try {
            chatTranscriptService.recordAssistantTurn(currentUser, sessionId, assistantText,
                    retrievalCtx.chunks(), lowConfidence);
        } catch (RuntimeException ex) {
            log.warn("agent.stream assistant-transcript persistence failed user={} session={}: {}",
                    currentUser.getUserId(), sessionId, ex.toString());
        }
    }

    private void accumulate(ChatClientResponse response, StringBuilder aggregatedText,
                            AtomicReference<ChatResponse> lastChatResponse) {
        ChatResponse chatResponse = response == null ? null : response.chatResponse();
        if (chatResponse == null) {
            return;
        }
        lastChatResponse.set(chatResponse);
        List<Generation> results = chatResponse.getResults();
        if (results == null || results.isEmpty() || results.getFirst().getOutput() == null) {
            return;
        }
        String delta = results.getFirst().getOutput().getText();
        if (delta != null) {
            aggregatedText.append(delta);
        }
    }

    private boolean isClientDisconnect(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof AsyncRequestNotUsableException) {
                return true;
            }
            String name = cause.getClass().getName();
            if (name.endsWith("ClientAbortException")) {
                return true;
            }
        }
        return false;
    }

    private SseEmitter produce(Consumer<UiMessageStreamWriter> producer) {
        SseEmitter emitter = new SseEmitter(0L);
        coachStreamExecutor.execute(() -> {
            try {
                transport.writeTo(emitter, producer::accept);
            } catch (Exception ex) {
                log.error("Failed to write to SSE emitter", ex);
            }
        });
        return emitter;
    }
}
