package org.aiincubator.ilmai.agent.service;

import org.aiincubator.ilmai.agent.RetrievedChunk;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class AgentRetrievalContext {

    private static final ThreadLocal<AgentRetrievalContext> CURRENT = new ThreadLocal<>();

    private final List<RetrievedChunk> chunks = new ArrayList<>();
    private int callCount = 0;

    private AgentRetrievalContext() {
    }

    public static AgentRetrievalContext begin() {
        AgentRetrievalContext ctx = new AgentRetrievalContext();
        CURRENT.set(ctx);
        return ctx;
    }

    public static AgentRetrievalContext create() {
        return new AgentRetrievalContext();
    }

    public static AgentRetrievalContext current() {
        return CURRENT.get();
    }

    public static void clear() {
        CURRENT.remove();
    }

    public synchronized void recordCall(List<RetrievedChunk> result) {
        callCount++;
        if (result != null) {
            chunks.addAll(result);
        }
    }

    public synchronized int callCount() {
        return callCount;
    }

    public synchronized List<RetrievedChunk> chunks() {
        return Collections.unmodifiableList(new ArrayList<>(chunks));
    }

    public synchronized boolean hasGrounding() {
        return !chunks.isEmpty();
    }
}
