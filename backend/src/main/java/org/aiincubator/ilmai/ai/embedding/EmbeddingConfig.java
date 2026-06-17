package org.aiincubator.ilmai.ai.embedding;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(GoogleGenAiEmbeddingProperties.class)
@ConditionalOnExpression("'${ai.embedding.api-key:}'.trim().length() > 0")
public class EmbeddingConfig {

    @Bean
    @ConditionalOnMissingBean(EmbeddingModel.class)
    public GoogleGenAiEmbeddingModel googleGenAiEmbeddingModel(GoogleGenAiEmbeddingProperties properties) {
        return new GoogleGenAiEmbeddingModel(properties);
    }
}
