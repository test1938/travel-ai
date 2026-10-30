package dev.travelpilot.knowledge;

import dev.travelpilot.common.ai.AiModels;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Ingests knowledge at startup (after the catalog seed):
 * <ul>
 *   <li>always with {@code --ingest};</li>
 *   <li>when {@code travelpilot.ingest.auto=true} and the vector store is still empty (first start of a
 *       container);</li>
 *   <li>when the stored vectors come from another embedding model than the configured one (provider switch):
 *       vectors of two models are not comparable, so retrieval would silently return noise.</li>
 * </ul>
 */
@Component
@Order(2)
public class IngestRunner implements ApplicationRunner {

    /** Rows ingested before the id was recorded were all embedded with the original Gemini model. */
    static final String LEGACY_EMBEDDING_ID = AiModels.GEMINI + ":gemini-embedding-001";

    private static final Logger log = LoggerFactory.getLogger(IngestRunner.class);

    private final IngestionService ingestion;
    private final JdbcClient jdbc;
    private final boolean auto;
    private final String embeddingId;

    @Autowired
    public IngestRunner(IngestionService ingestion, JdbcClient jdbc,
            @Value("${travelpilot.ingest.auto:false}") boolean auto, AiModels models) {
        this(ingestion, jdbc, auto, models.embeddingId());
    }

    IngestRunner(IngestionService ingestion, JdbcClient jdbc, boolean auto, String embeddingId) {
        this.ingestion = ingestion;
        this.jdbc = jdbc;
        this.auto = auto;
        this.embeddingId = embeddingId;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (args.containsOption("ingest")) {
            ingestion.ingestAll();
            return;
        }
        Optional<String> stored = storedEmbeddingId();
        if (stored.isEmpty()) {
            if (auto) {
                log.info("Vector store is empty: ingesting the knowledge base once (travelpilot.ingest.auto)");
                ingestion.ingestAll();
            }
        } else if (!stored.get().equals(embeddingId)) {
            log.warn("Vector store was embedded with {} but the app now uses {}: re-ingesting the knowledge base",
                    stored.get(), embeddingId);
            ingestion.ingestAll();
        }
    }

    /** Embedding id of the stored vectors, or empty when the store has no rows. */
    Optional<String> storedEmbeddingId() {
        return jdbc.sql("SELECT coalesce(metadata->>'" + IngestionService.EMBEDDING_ID + "', :legacy) FROM vector_store"
                        + " LIMIT 1")
                .param("legacy", LEGACY_EMBEDDING_ID)
                .query(String.class)
                .optional();
    }
}
