package org.aiincubator.ilmai.agent.service;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

@Component
public class PlannerSystemPrompt {

    private static final String RESOURCE_PATH = "prompts/agent/planner-system.txt";

    private final String prompt;

    public PlannerSystemPrompt() {
        this.prompt = load();
    }

    public String get() {
        return prompt;
    }

    private String load() {
        ClassPathResource resource = new ClassPathResource(RESOURCE_PATH);
        if (!resource.exists()) {
            throw new IllegalStateException("Planner system prompt missing: " + RESOURCE_PATH);
        }
        try {
            return StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8).trim();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to load planner system prompt: " + RESOURCE_PATH, e);
        }
    }
}
