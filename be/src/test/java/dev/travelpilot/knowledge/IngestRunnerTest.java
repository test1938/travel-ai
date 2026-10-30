package dev.travelpilot.knowledge;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * T9.04: a fresh container ingests once; later starts do not pay for embeddings again. Switching the embedding
 * provider re-ingests, because old vectors live in another vector space.
 */
class IngestRunnerTest {

    private static final String OPENAI = "openai:text-embedding-3-small";

    private final IngestionService ingestion = mock(IngestionService.class);
    private final JdbcClient jdbc = mock(JdbcClient.class, RETURNS_DEEP_STUBS);

    private void storeHas(Optional<String> embeddingId) {
        when(jdbc.sql(anyString()).param(eq("legacy"), anyString()).query(String.class).optional())
                .thenReturn(embeddingId);
    }

    private IngestRunner runner(boolean auto, String embeddingId) {
        return new IngestRunner(ingestion, jdbc, auto, embeddingId);
    }

    @Test
    void autoIngestsWhenTheStoreIsEmpty() {
        storeHas(Optional.empty());
        runner(true, OPENAI).run(new DefaultApplicationArguments());
        verify(ingestion).ingestAll();
    }

    @Test
    void skipsWhenTheStoreHasVectorsOfTheSameModel() {
        storeHas(Optional.of(OPENAI));
        runner(true, OPENAI).run(new DefaultApplicationArguments());
        verify(ingestion, never()).ingestAll();
    }

    @Test
    void reIngestsAfterAnEmbeddingProviderSwitch() {
        storeHas(Optional.of(IngestRunner.LEGACY_EMBEDDING_ID));
        runner(false, OPENAI).run(new DefaultApplicationArguments());
        verify(ingestion).ingestAll();
    }

    @Test
    void ingestFlagAlwaysIngestsAndAnEmptyStoreWaitsWithoutAuto() {
        storeHas(Optional.empty());
        runner(false, OPENAI).run(new DefaultApplicationArguments());
        verify(ingestion, never()).ingestAll();

        runner(false, OPENAI).run(new DefaultApplicationArguments("--ingest"));
        verify(ingestion).ingestAll();
    }
}
