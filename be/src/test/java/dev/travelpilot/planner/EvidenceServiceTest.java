package dev.travelpilot.planner;

import static org.assertj.core.api.Assertions.assertThat;

import dev.travelpilot.integration.weather.Forecast;
import dev.travelpilot.knowledge.IngestionService;
import dev.travelpilot.knowledge.KnowledgeLoader;
import dev.travelpilot.knowledge.Retriever;
import dev.travelpilot.recommendation.CandidateSelector;
import dev.travelpilot.recommendation.CandidateSet;
import dev.travelpilot.recommendation.HardFilter;
import dev.travelpilot.recommendation.ScoredPlace;
import dev.travelpilot.support.Personas;
import dev.travelpilot.support.StubEmbeddingModel;
import dev.travelpilot.support.TestProps;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.ai.vectorstore.SimpleVectorStore;

class EvidenceServiceTest {

    static EvidenceService service;

    @BeforeAll
    static void setUp() {
        SimpleVectorStore store = SimpleVectorStore.builder(new StubEmbeddingModel(64)).build();
        new IngestionService(new KnowledgeLoader(), store, TestProps.defaults(), "test:stub").ingestAll();
        service = new EvidenceService(new Retriever(store, TestProps.defaults()));
    }

    @Test
    void evidenceIsNumberedAndAboutTopCandidates() {
        CandidateSet candidates = new CandidateSelector(TestProps.defaults().scoring().weights())
                .select(Personas.CATALOG, Personas.family(), Forecast.NONE, HardFilter.Options.defaults());

        List<PlanEvidence> evidence = service.forCandidates(Personas.family(), candidates);

        assertThat(evidence).isNotEmpty().hasSizeLessThanOrEqualTo(Retriever.MAX_FOR_PLACES);
        assertThat(evidence).extracting(PlanEvidence::evId)
                .containsExactlyElementsOf(IntStream.rangeClosed(1, evidence.size()).mapToObj(i -> "ev-" + i).toList());
        Set<String> candidateIds = candidates.all().map(ScoredPlace::id).collect(Collectors.toSet());
        assertThat(evidence).allSatisfy(e -> assertThat(candidateIds).contains(e.evidence().placeId()));
        assertThat(evidence.getFirst().toSource().id()).isEqualTo("ev-1");
    }

    @Test
    void queryDescribesTheTraveler() {
        assertThat(EvidenceService.query(Personas.family()))
                .contains("Đà Nẵng", "gia đình có trẻ em", "beach", "theme park");
        assertThat(EvidenceService.query(Personas.couple())).contains("cặp đôi");
        assertThat(EvidenceService.query(Personas.friends())).contains("nhóm bạn");
    }
}
