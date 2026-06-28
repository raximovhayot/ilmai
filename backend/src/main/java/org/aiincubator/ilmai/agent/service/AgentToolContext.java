package org.aiincubator.ilmai.agent.service;

import org.aiincubator.ilmai.common.CurrentUser;
import org.springframework.ai.chat.model.ToolContext;
import uz.uzinfoweb.uimessagestream.spring.RecordingToolCallingManager;
import uz.uzinfoweb.uimessagestream.spring.SerializedPartSink;

import java.util.Map;
import java.util.UUID;

public final class AgentToolContext {

    public static final String CURRENT_USER_KEY = "agent.current_user";
    public static final String RETRIEVAL_CONTEXT_KEY = "agent.retrieval_context";
    public static final String ROOM_ID_KEY = "agent.room_id";

    private AgentToolContext() {
    }

    public static CurrentUser currentUser(ToolContext toolContext) {
        Object value = contextValue(toolContext, CURRENT_USER_KEY);
        return value instanceof CurrentUser user ? user : null;
    }

    public static AgentRetrievalContext retrievalContext(ToolContext toolContext) {
        Object value = contextValue(toolContext, RETRIEVAL_CONTEXT_KEY);
        return value instanceof AgentRetrievalContext ctx ? ctx : null;
    }

    public static UUID roomId(ToolContext toolContext) {
        Object value = contextValue(toolContext, ROOM_ID_KEY);
        return value instanceof UUID roomId ? roomId : null;
    }

    public static SerializedPartSink sink(ToolContext toolContext) {
        Object value = contextValue(toolContext, RecordingToolCallingManager.SINK_KEY);
        return value instanceof SerializedPartSink sink ? sink : null;
    }

    private static Object contextValue(ToolContext toolContext, String key) {
        if (toolContext == null) {
            return null;
        }
        Map<String, Object> context = toolContext.getContext();
        if (context == null) {
            return null;
        }
        return context.get(key);
    }

    public static CurrentUser requireCurrentUser(ToolContext toolContext) {
        CurrentUser user = currentUser(toolContext);
        if (user == null) {
            throw new IllegalStateException("Required CurrentUser is missing from ToolContext");
        }
        return user;
    }

    public static UUID requireUserId(ToolContext toolContext) {
        CurrentUser user = requireCurrentUser(toolContext);
        if (user.getUserId() == null) {
            throw new IllegalStateException("CurrentUser inside ToolContext has no userId");
        }
        return user.getUserId();
    }
}
