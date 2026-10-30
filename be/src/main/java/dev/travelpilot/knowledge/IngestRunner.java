package dev.travelpilot.knowledge;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** Ingests knowledge at startup when the app is started with {@code --ingest} (after the catalog seed). */
@Component
@Order(2)
public class IngestRunner implements ApplicationRunner {

    private final IngestionService ingestion;

    public IngestRunner(IngestionService ingestion) {
        this.ingestion = ingestion;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (args.containsOption("ingest")) {
            ingestion.ingestAll();
        }
    }
}
