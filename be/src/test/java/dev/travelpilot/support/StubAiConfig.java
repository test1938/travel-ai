package dev.travelpilot.support;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** Replaces the Gemini models with offline stubs. Use together with the "test" profile. */
@TestConfiguration(proxyBeanMethods = false)
public class StubAiConfig {

    /** Must match spring.ai.vectorstore.pgvector.dimensions. */
    public static final int DIMENSIONS = 768;

    @Bean
    @Primary
    StubChatModel stubChatModel() {
        return new StubChatModel();
    }

    @Bean
    @Primary
    EmbeddingModel stubEmbeddingModel() {
        return new StubEmbeddingModel(DIMENSIONS);
    }
}
