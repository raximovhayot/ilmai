package org.aiincubator.ilmai.ai.ingestion.support;

import org.aiincubator.ilmai.common.storage.BlobStorage;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.core.task.SyncTaskExecutor;

import java.util.concurrent.Executor;

@TestConfiguration(proxyBeanMethods = false)
public class IntegrationTestConfiguration {

    @Bean
    @Primary
    public EmbeddingModel testEmbeddingModel() {
        return new BagOfWordsTestEmbeddingModel();
    }

    @Bean
    @Primary
    public BlobStorage testBlobStorage() {
        return new InMemoryBlobStorage();
    }

    @Bean
    @Primary
    public Executor applicationTaskExecutor() {
        return new SyncTaskExecutor();
    }
}
