package org.aiincubator.ilmai.ai.embedding;

import com.google.genai.types.EmbedContentConfig;
import com.google.genai.types.EmbedContentResponse;

import java.util.List;

@FunctionalInterface
interface EmbedContentInvoker {

    EmbedContentResponse embedContent(String model, List<String> inputs, EmbedContentConfig config);
}
