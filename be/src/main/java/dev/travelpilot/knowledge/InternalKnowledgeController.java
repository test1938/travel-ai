package dev.travelpilot.knowledge;

import dev.travelpilot.common.domain.Destination;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Developer-only endpoints; the bean does not exist outside the "dev" profile. */
@Profile("dev")
@RestController
@RequestMapping("/internal")
public class InternalKnowledgeController {

    private final IngestionService ingestion;
    private final Retriever retriever;

    public InternalKnowledgeController(IngestionService ingestion, Retriever retriever) {
        this.ingestion = ingestion;
        this.retriever = retriever;
    }

    @PostMapping("/ingest")
    public IngestionService.IngestReport ingest() {
        return ingestion.ingestAll();
    }

    /** Raw retrieval for debugging: top-k chunks with score, no threshold. */
    @GetMapping("/search")
    public List<SearchHit> search(
            @RequestParam @NotBlank @Size(max = 500) String q,
            @RequestParam Destination dest,
            @RequestParam(defaultValue = "5") @Min(1) @Max(20) int k) {
        return retriever.search(dest, q, k, 0.0).stream()
                .map(e -> new SearchHit(e.score(), e.docId(), e.docType(), e.title(), e.snippet()))
                .toList();
    }

    public record SearchHit(double score, String docId, DocType docType, String title, String snippet) {}
}
