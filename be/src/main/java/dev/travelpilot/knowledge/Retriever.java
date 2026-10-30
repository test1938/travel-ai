package dev.travelpilot.knowledge;

import dev.travelpilot.common.config.TravelPilotProperties;
import dev.travelpilot.common.domain.Destination;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.stereotype.Component;

/**
 * Vector search over the knowledge chunks, always restricted to one destination. Filters are built from enums
 * and catalog ids, never from user text, so a question cannot change the filter.
 */
@Component
public class Retriever {

    /** Evidence limits for planning (plan/03-ai-pipeline.md §7). */
    public static final int MAX_PER_PLACE = 2;
    public static final int MAX_FOR_PLACES = 12;

    private final VectorStore vectorStore;
    private final int defaultTopK;
    private final double defaultThreshold;

    public Retriever(VectorStore vectorStore, TravelPilotProperties props) {
        this.vectorStore = vectorStore;
        this.defaultTopK = props.rag().topK();
        this.defaultThreshold = props.rag().threshold();
    }

    public List<Evidence> search(Destination destination, String query) {
        return search(destination, query, defaultTopK, defaultThreshold);
    }

    public List<Evidence> search(Destination destination, String query, int topK, double threshold) {
        FilterExpressionBuilder b = new FilterExpressionBuilder();
        return run(query, topK, threshold, b.eq("destination", destination.name()).build());
    }

    /**
     * Evidence about specific catalog places: at most {@value #MAX_PER_PLACE} chunks per place and
     * {@value #MAX_FOR_PLACES} in total, best score first. No similarity threshold: the filter already
     * guarantees relevance.
     */
    public List<Evidence> forPlaces(Destination destination, Collection<String> placeIds, String query) {
        if (placeIds.isEmpty()) {
            return List.of();
        }
        FilterExpressionBuilder b = new FilterExpressionBuilder();
        Filter.Expression filter = b.and(
                b.eq("destination", destination.name()),
                b.in("placeId", new ArrayList<Object>(placeIds))).build();
        int topK = Math.min(placeIds.size() * MAX_PER_PLACE * 2, 50);
        List<Evidence> hits = run(query, topK, SearchRequest.SIMILARITY_THRESHOLD_ACCEPT_ALL, filter);

        Map<String, Integer> perPlace = new HashMap<>();
        List<Evidence> limited = new ArrayList<>();
        for (Evidence e : hits) {
            if (limited.size() == MAX_FOR_PLACES) {
                break;
            }
            if (perPlace.merge(e.placeId(), 1, Integer::sum) <= MAX_PER_PLACE) {
                limited.add(e);
            }
        }
        return limited;
    }

    private List<Evidence> run(String query, int topK, double threshold, Filter.Expression filter) {
        SearchRequest request = SearchRequest.builder()
                .query(query)
                .topK(topK)
                .similarityThreshold(threshold)
                .filterExpression(filter)
                .build();
        return vectorStore.similaritySearch(request).stream().map(Retriever::toEvidence).toList();
    }

    @SuppressWarnings("unchecked")
    static Evidence toEvidence(Document d) {
        Map<String, Object> m = d.getMetadata();
        Object placeIds = m.get("placeIds");
        return new Evidence(
                (String) m.get("docId"),
                DocType.valueOf((String) m.get("docType")),
                (String) m.get("placeId"),
                placeIds instanceof List<?> list ? (List<String>) list : List.of(),
                (String) m.get("title"),
                d.getText(),
                (String) m.get("source"),
                (String) m.get("sourceUrl"),
                (String) m.get("license"),
                d.getScore() == null ? 0 : d.getScore());
    }
}
