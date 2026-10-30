package dev.travelpilot.knowledge;

import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.jdbc.core.simple.JdbcClient;

/** T9.04: a fresh container ingests once; later starts do not pay for embeddings again. */
class IngestRunnerTest {

    private final IngestionService ingestion = mock(IngestionService.class);
    private final JdbcClient jdbc = mock(JdbcClient.class, RETURNS_DEEP_STUBS);

    private void storeHas(long rows) {
        when(jdbc.sql("SELECT count(*) FROM vector_store").query(Long.class).single()).thenReturn(rows);
    }

    @Test
    void autoIngestsWhenTheStoreIsEmpty() {
        storeHas(0);
        new IngestRunner(ingestion, jdbc, true).run(new DefaultApplicationArguments());
        verify(ingestion).ingestAll();
    }

    @Test
    void autoSkipsWhenTheStoreHasChunks() {
        storeHas(401);
        new IngestRunner(ingestion, jdbc, true).run(new DefaultApplicationArguments());
        verify(ingestion, never()).ingestAll();
    }

    @Test
    void ingestFlagAlwaysIngestsAndNothingHappensByDefault() {
        new IngestRunner(ingestion, jdbc, false).run(new DefaultApplicationArguments());
        verify(ingestion, never()).ingestAll();

        new IngestRunner(ingestion, jdbc, false).run(new DefaultApplicationArguments("--ingest"));
        verify(ingestion).ingestAll();
    }
}
