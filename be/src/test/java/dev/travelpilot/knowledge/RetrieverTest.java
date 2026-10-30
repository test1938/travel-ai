package dev.travelpilot.knowledge;

import static org.assertj.core.api.Assertions.assertThat;

import dev.travelpilot.common.domain.Destination;
import dev.travelpilot.support.StubEmbeddingModel;
import dev.travelpilot.support.TestProps;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.ai.vectorstore.SimpleVectorStore;

/** Offline: SimpleVectorStore evaluates the same filter expressions as pgvector. */
class RetrieverTest {

    static Retriever retriever;
    static List<KnowledgeChunk> chunks;

    @BeforeAll
    static void ingest() {
        SimpleVectorStore store = SimpleVectorStore.builder(new StubEmbeddingModel(64)).build();
        new IngestionService(new KnowledgeLoader(), store, TestProps.defaults()).ingestAll();
        retriever = new Retriever(store, TestProps.defaults());
        chunks = new KnowledgeLoader().loadAll();
    }

    @ParameterizedTest
    @EnumSource(Destination.class)
    void resultsNeverLeaveTheRequestedDestination(Destination destination) {
        List<Evidence> hits = retriever.search(destination, "đi đâu chơi với trẻ em", 50, 0.0);

        assertThat(hits).isNotEmpty();
        Map<String, Destination> destinationOf =
                chunks.stream().collect(Collectors.toMap(KnowledgeChunk::docId, KnowledgeChunk::destination));
        assertThat(hits).allSatisfy(e -> assertThat(destinationOf.get(e.docId())).isEqualTo(destination));
    }

    @Test
    void identicalTextRanksFirstWithFullScore() {
        KnowledgeChunk target = chunks.stream()
                .filter(c -> c.docId().equals("hanoi-tips-weather-02")).findFirst().orElseThrow();

        List<Evidence> hits = retriever.search(Destination.HANOI, target.content(), 5, 0.0);

        assertThat(hits.getFirst().docId()).isEqualTo("hanoi-tips-weather-02");
        assertThat(hits.getFirst().score()).isGreaterThan(0.99);
        assertThat(hits.getFirst().license()).isNotBlank();
    }

    @Test
    void sameTextInAnotherDestinationIsNotReturned() {
        KnowledgeChunk hanoiChunk = chunks.stream()
                .filter(c -> c.docId().equals("hanoi-tips-weather-02")).findFirst().orElseThrow();

        List<Evidence> hits = retriever.search(Destination.DANANG, hanoiChunk.content(), 10, 0.0);

        assertThat(hits).extracting(Evidence::docId).doesNotContain("hanoi-tips-weather-02");
    }

    @Test
    void forPlacesKeepsOnlyRequestedPlacesWithinLimits() {
        List<String> placeIds = List.of("danang-way-359114064", "danang-way-694831926", "danang-relation-8552348");

        List<Evidence> hits = retriever.forPlaces(Destination.DANANG, placeIds, "gia đình có trẻ nhỏ");

        assertThat(hits).isNotEmpty().hasSizeLessThanOrEqualTo(Retriever.MAX_FOR_PLACES);
        assertThat(hits).allSatisfy(e -> assertThat(placeIds).contains(e.placeId()));
        assertThat(hits.stream().collect(Collectors.groupingBy(Evidence::placeId, Collectors.counting())))
                .allSatisfy((id, n) -> assertThat(n).isLessThanOrEqualTo(Retriever.MAX_PER_PLACE));
    }

    @Test
    void forPlacesWithoutIdsReturnsNothing() {
        assertThat(retriever.forPlaces(Destination.HANOI, List.of(), "x")).isEmpty();
    }

    @Test
    void snippetIsShortAndWithoutTitle() {
        Evidence e = retriever.search(Destination.HANOI, "Văn Miếu", 1, 0.0).getFirst();
        assertThat(e.snippet()).hasSizeLessThanOrEqualTo(Evidence.SNIPPET_CHARS).doesNotStartWith(e.title() + "\n");
    }
}
