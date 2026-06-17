package org.aiincubator.ilmai.agent.service;

import org.aiincubator.ilmai.agent.ActionPart;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class AgentActionContext {

    private static final ThreadLocal<AgentActionContext> CURRENT = new ThreadLocal<>();

    private final List<ActionPart> actions = new ArrayList<>();

    private AgentActionContext() {
    }

    public static AgentActionContext begin() {
        AgentActionContext ctx = new AgentActionContext();
        CURRENT.set(ctx);
        return ctx;
    }

    public static AgentActionContext current() {
        return CURRENT.get();
    }

    public static void clear() {
        CURRENT.remove();
    }

    public void record(ActionPart action) {
        if (action != null) {
            actions.add(action);
        }
    }

    public List<ActionPart> actions() {
        return Collections.unmodifiableList(actions);
    }
}
