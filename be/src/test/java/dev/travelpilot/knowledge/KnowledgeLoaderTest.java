package dev.travelpilot.knowledge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.travelpilot.catalog.CatalogReader;
import dev.travelpilot.catalog.Place;
import dev.travelpilot.common.domain.Destination;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class KnowledgeLoaderTest {

    private final KnowledgeLoader loader = new KnowledgeLoader();

    @Test
    void loadsAll401VietnameseChunks() {
        List<KnowledgeChunk> chunks = loader.loadAll();

        assertThat(chunks).hasSize(401);
        assertThat(chunks).allMatch(c -> "vi".equals(c.lang()));
        Map<Destination, Long> perDestination =
                chunks.stream().collect(Collectors.groupingBy(KnowledgeChunk::destination, Collectors.counting()));
        assertThat(perDestination).containsEntry(Destination.HANOI, 166L)
                .containsEntry(Destination.DANANG, 162L)
                .containsEntry(Destination.QUANGNINH, 73L);
    }

    @Test
    void everyPlaceChunkPointsToOneCatalogPlace() {
        Set<String> catalogIds = new CatalogReader().readAll().stream().map(Place::id).collect(Collectors.toSet());
        List<KnowledgeChunk> placeChunks =
                loader.loadAll().stream().filter(c -> c.docType() == DocType.PLACE).toList();

        assertThat(placeChunks).hasSize(101);
        assertThat(placeChunks).allSatisfy(c -> assertThat(catalogIds).contains(c.singlePlaceId()));
    }

    @Test
    void contentIsTitlePlusText() {
        KnowledgeChunk chunk = loader.loadAll().getFirst();
        assertThat(chunk.content()).isEqualTo(chunk.title() + "\n" + chunk.text());
    }

    @Test
    void missingFieldNamesFileAndLine() {
        assertThatThrownBy(() -> loader.loadAll("classpath:fixtures/knowledge/knowledge_missing_text.jsonl"))
                .isInstanceOf(KnowledgeLoader.KnowledgeException.class)
                .hasMessageContaining("knowledge_missing_text.jsonl:2")
                .hasMessageContaining("[text]")
                .hasMessageContaining("bad-02");
    }

    @Test
    void brokenJsonNamesFileAndLine() {
        assertThatThrownBy(() -> loader.loadAll("classpath:fixtures/knowledge/knowledge_broken_json.jsonl"))
                .isInstanceOf(KnowledgeLoader.KnowledgeException.class)
                .hasMessageContaining("knowledge_broken_json.jsonl:1")
                .hasMessageContaining("invalid JSON");
    }
}
