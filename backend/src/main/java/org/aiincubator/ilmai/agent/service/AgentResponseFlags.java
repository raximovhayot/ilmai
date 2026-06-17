package org.aiincubator.ilmai.agent.service;

public final class AgentResponseFlags {

    private static final ThreadLocal<AgentResponseFlags> CURRENT = new ThreadLocal<>();

    private boolean lowConfidence;

    private AgentResponseFlags() {
    }

    public static AgentResponseFlags begin() {
        AgentResponseFlags flags = new AgentResponseFlags();
        CURRENT.set(flags);
        return flags;
    }

    public static AgentResponseFlags current() {
        return CURRENT.get();
    }

    public static void clear() {
        CURRENT.remove();
    }

    public boolean isLowConfidence() {
        return lowConfidence;
    }

    public void markLowConfidence() {
        this.lowConfidence = true;
    }
}
