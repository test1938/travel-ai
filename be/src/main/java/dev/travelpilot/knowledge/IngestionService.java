package dev.travelpilot.knowledge;

import dev.travelpilot.common.config.TravelPilotProperties;
import dev.travelpilot.common.domain.Destination;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.stereotype.Service;

/**
 * Embeds the knowledge chunks into the vector store. Idempotent: each destination is deleted then re-added,
 * and document ids are UUIDs derived from docId, so running it twice leaves the same 401 rows.
 */
@Service
public class IngestionService {

    private static final Logger log = LoggerFactory.getLogger(IngestionService.class);

    private final KnowledgeLoader loader;
    private final VectorStore vectorStore;
    private final int batchSize;

    public IngestionService(KnowledgeLoader loader, VectorStore vectorStore, TravelPilotProperties props) {
        this.loader = loader;
        this.vectorStore = vectorStore;
        this.batchSize = props.ingest().batchSize();
    }

    /** Per-destination chunk counts and total duration of one ingestion run. */
    public record IngestReport(Map<Destination, Integer> chunksPerDestination, int totalChunks, long durationMs) {}

    public IngestReport ingestAll() {
        long start = System.currentTimeMillis();
        Map<Destination, List<KnowledgeChunk>> byDestination = loader.loadAll().stream()
                .collect(Collectors.groupingBy(KnowledgeChunk::destination,
                        () -> new EnumMap<>(Destination.class), Collectors.toList()));

        Map<Destination, Integer> counts = new EnumMap<>(Destination.class);
        byDestination.forEach((destination, chunks) -> {
            replace(destination, chunks.stream().map(IngestionService::toDocument).toList());
            counts.put(destination, chunks.size());
        });

        int total = counts.values().stream().mapToInt(Integer::intValue).sum();
        long duration = System.currentTimeMillis() - start;
        log.info("Ingested {} knowledge chunks {} in {} ms", total, counts, duration);
        return new IngestReport(counts, total, duration);
    }

    private void replace(Destination destination, List<Document> documents) {
        vectorStore.delete(new FilterExpressionBuilder().eq("destination", destination.name()).build());
        for (int from = 0; from < documents.size(); from += batchSize) {
            vectorStore.add(documents.subList(from, Math.min(from + batchSize, documents.size())));
        }
    }

    /** Deterministic id + metadata without null values (Spring AI rejects null metadata). */
    static Document toDocument(KnowledgeChunk c) {
        Map<String, Object> meta = new HashMap<>();
        meta.put("docId", c.docId());
        meta.put("destination", c.destination().name());
        meta.put("docType", c.docType().name());
        meta.put("lang", c.lang());
        meta.put("title", c.title());
        meta.put("placeIds", c.placeIds());
        putIfPresent(meta, "placeId", c.singlePlaceId());
        putIfPresent(meta, "source", c.source());
        putIfPresent(meta, "sourceUrl", c.sourceUrl());
        putIfPresent(meta, "license", c.license());
        putIfPresent(meta, "retrievedAt", c.retrievedAt());
        putIfPresent(meta, "translatedFrom", c.translatedFrom());
        return new Document(documentId(c.docId()), c.content(), meta);
    }

    static String documentId(String docId) {
        return UUID.nameUUIDFromBytes(docId.getBytes(StandardCharsets.UTF_8)).toString();
    }

    private static void putIfPresent(Map<String, Object> meta, String key, String value) {
        if (value != null && !value.isBlank()) {
            meta.put(key, value);
        }
    }
}
