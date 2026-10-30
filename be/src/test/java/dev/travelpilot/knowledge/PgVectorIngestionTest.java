package dev.travelpilot.knowledge;

import static org.assertj.core.api.Assertions.assertThat;

import dev.travelpilot.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

/** Needs Docker: ingests into the real vector_store table (stub embeddings). */
@IntegrationTest
class PgVectorIngestionTest {

    @Autowired
    IngestionService ingestion;

    @Autowired
    JdbcClient jdbc;

    @Test
    void ingestingTwiceKeeps401Rows() {
        ingestion.ingestAll();
        assertThat(count()).isEqualTo(401);
        ingestion.ingestAll();
        assertThat(count()).isEqualTo(401);
    }

    @Test
    void metadataIsQueryableAsJson() {
        ingestion.ingestAll();
        Integer hanoi = jdbc.sql("SELECT count(*) FROM vector_store WHERE metadata->>'destination' = 'HANOI'")
                .query(Integer.class).single();
        assertThat(hanoi).isEqualTo(166);
    }

    private int count() {
        return jdbc.sql("SELECT count(*) FROM vector_store").query(Integer.class).single();
    }
}
