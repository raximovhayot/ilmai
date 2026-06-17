package org.aiincubator.ilmai.ai.ingestion.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest
@Import(IntegrationTestConfiguration.class)
@TestPropertySource(properties = {
        "ai.embedding.api-key=integration-test-key",
        "spring.ai.google.genai.api-key=integration-test-key",
        "spring.docker.compose.enabled=false",
        "auth.jwt.secret=integration-test-jwt-secret-32-chars-long-x",
        "auth.google.client-id=integration-test-google-client-id"
})
public abstract class AbstractEmbeddingIntegrationTest {

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"))
            .withDatabaseName("ilmai_test")
            .withUsername("test")
            .withPassword("test");

    static {
        POSTGRES.start();
    }
}
