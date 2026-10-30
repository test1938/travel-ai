package dev.travelpilot.knowledge;

import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Developer-only endpoints; the bean does not exist outside the "dev" profile. */
@Profile("dev")
@RestController
@RequestMapping("/internal")
public class InternalKnowledgeController {

    private final IngestionService ingestion;

    public InternalKnowledgeController(IngestionService ingestion) {
        this.ingestion = ingestion;
    }

    @PostMapping("/ingest")
    public IngestionService.IngestReport ingest() {
        return ingestion.ingestAll();
    }
}
