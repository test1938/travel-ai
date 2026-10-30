package dev.travelpilot.knowledge;

import static org.assertj.core.api.Assertions.assertThat;

import dev.travelpilot.common.domain.Destination;
import dev.travelpilot.support.StubEmbeddingModel;
import dev.travelpilot.support.TestProps;
import java.io.File;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/** Offline: in-memory SimpleVectorStore + deterministic stub embeddings. */
class IngestionServiceTest {

    @TempDir
    Path tmp;

    private SimpleVectorStore store;
    private IngestionService ingestion;

    @BeforeEach
    void setUp() {
        store = SimpleVectorStore.builder(new StubEmbeddingModel(64)).build();
        ingestion = new IngestionService(new KnowledgeLoader(), store, TestProps.defaults(), "test:stub");
    }

    @Test
    void ingestingTwiceKeeps401Documents() {
        IngestionService.IngestReport first = ingestion.ingestAll();
        assertThat(first.totalChunks()).isEqualTo(401);
        assertThat(first.chunksPerDestination()).containsEntry(Destination.HANOI, 166)
                .containsEntry(Destination.DANANG, 162)
                .containsEntry(Destination.QUANGNINH, 73);
        assertThat(allDocuments()).hasSize(401);

        ingestion.ingestAll();
        assertThat(allDocuments()).hasSize(401);
    }

    @Test
    void documentIdsAreDeterministicUuids() {
        assertThat(IngestionService.documentId("hanoi-tips-weather-02"))
                .isEqualTo(IngestionService.documentId("hanoi-tips-weather-02"))
                .matches("[0-9a-f]{8}-[0-9a-f]{4}-3[0-9a-f]{3}-[0-9a-f]{4}-[0-9a-f]{12}");
    }

    @Test
    void placeChunkCarriesScalarPlaceIdAndNoNullMetadata() {
        KnowledgeChunk baNa = new KnowledgeLoader().loadAll().stream()
                .filter(c -> c.docId().equals("danang-place-danang-way-359114064")).findFirst().orElseThrow();

        Document doc = IngestionService.toDocument(baNa, "test:stub");

        assertThat(doc.getId()).isEqualTo(IngestionService.documentId(baNa.docId()));
        assertThat(doc.getText()).startsWith(baNa.title() + "\n");
        assertThat(doc.getMetadata()).containsEntry("placeId", "danang-way-359114064")
                .containsEntry("destination", "DANANG")
                .containsEntry("docType", "PLACE")
                .doesNotContainValue(null);
    }

    /** SimpleVectorStore has no count/list API; its JSON dump is a map keyed by document id. */
    private Map<String, Object> allDocuments() {
        File file = tmp.resolve("store.json").toFile();
        store.save(file);
        return JsonMapper.builder().build().readValue(file, new TypeReference<Map<String, Object>>() {});
    }
}
