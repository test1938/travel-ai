package dev.travelpilot.knowledge;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Ingests knowledge at startup (after the catalog seed): always with {@code --ingest}, or when
 * {@code travelpilot.ingest.auto=true} and the vector store is still empty (first start of a container).
 */
@Component
@Order(2)
public class IngestRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(IngestRunner.class);

    private final IngestionService ingestion;
    private final JdbcClient jdbc;
    private final boolean auto;

    public IngestRunner(IngestionService ingestion, JdbcClient jdbc,
            @Value("${travelpilot.ingest.auto:false}") boolean auto) {
        this.ingestion = ingestion;
        this.jdbc = jdbc;
        this.auto = auto;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (args.containsOption("ingest")) {
            ingestion.ingestAll();
        } else if (auto && storeIsEmpty()) {
            log.info("Vector store is empty: ingesting the knowledge base once (travelpilot.ingest.auto)");
            ingestion.ingestAll();
        }
    }

    boolean storeIsEmpty() {
        Long rows = jdbc.sql("SELECT count(*) FROM vector_store").query(Long.class).single();
        return rows == null || rows == 0;
    }
}
