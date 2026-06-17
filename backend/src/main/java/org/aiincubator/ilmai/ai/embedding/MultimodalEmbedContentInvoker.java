package org.aiincubator.ilmai.ai.embedding;

import com.google.genai.types.Content;
import com.google.genai.types.EmbedContentConfig;
import com.google.genai.types.EmbedContentResponse;

@FunctionalInterface
interface MultimodalEmbedContentInvoker {

    EmbedContentResponse embedContent(String model, Content content, EmbedContentConfig config);
}
