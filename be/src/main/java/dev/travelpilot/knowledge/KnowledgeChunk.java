package dev.travelpilot.knowledge;

import dev.travelpilot.common.domain.Destination;
import java.util.List;

/**
 * One line of knowledge_*.jsonl (plan/01-dataset.md §7). Already sized for embedding: no further splitting.
 * Optional: sourceUrl, retrievedAt, translatedFrom, originalTitle.
 */
public record KnowledgeChunk(
        String docId,
        Destination destination,
        DocType docType,
        String lang,
        String title,
        String text,
        List<String> placeIds,
        String source,
        String sourceUrl,
        String license,
        String retrievedAt,
        String translatedFrom,
        String originalTitle) {

    public KnowledgeChunk {
        placeIds = placeIds == null ? List.of() : List.copyOf(placeIds);
    }

    /** Text that gets embedded and returned as evidence. */
    public String content() {
        return title + "\n" + text;
    }

    /** The single place a PLACE chunk is about, or null when the chunk covers zero or several places. */
    public String singlePlaceId() {
        return placeIds.size() == 1 ? placeIds.getFirst() : null;
    }
}
