package dev.travelpilot.planner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.travelpilot.catalog.PlaceRepository;
import dev.travelpilot.common.ai.LlmGateway;
import dev.travelpilot.common.ai.PromptLoader;
import dev.travelpilot.common.domain.Destination;
import dev.travelpilot.common.domain.Slot;
import dev.travelpilot.integration.weather.NoWeatherProvider;
import dev.travelpilot.integration.weather.WeatherProvider;
import dev.travelpilot.knowledge.IngestionService;
import dev.travelpilot.knowledge.KnowledgeLoader;
import dev.travelpilot.knowledge.Retriever;
import dev.travelpilot.recommendation.RecommendationService;
import dev.travelpilot.support.Personas;
import dev.travelpilot.support.StubChatModel;
import dev.travelpilot.support.StubEmbeddingModel;
import dev.travelpilot.support.TestProps;
import dev.travelpilot.trip.TripPlan;
import dev.travelpilot.trip.Warning;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.vectorstore.SimpleVectorStore;

/** The whole workflow offline: real filter/score/validator/assembler, stub LLM, in-memory vector store. */
class PlannerOrchestratorTest {

    static Retriever retriever;
    static PlaceRepository places;

    private final StubChatModel model = new StubChatModel();

    @BeforeAll
    static void setUp() {
        SimpleVectorStore store = SimpleVectorStore.builder(new StubEmbeddingModel(64)).build();
        new IngestionService(new KnowledgeLoader(), store, TestProps.defaults()).ingestAll();
        retriever = new Retriever(store, TestProps.defaults());
        places = mock(PlaceRepository.class);
        for (Destination d : Destination.values()) {
            when(places.findByDestination(d)).thenReturn(Personas.placesOf(d));
        }
    }

    private PlannerOrchestrator orchestrator(WeatherProvider weather) {
        PlannerClient client = new PlannerClient(ChatClient.create(model),
                new LlmGateway(Executors.newVirtualThreadPerTaskExecutor(), TestProps.defaults()),
                new PlannerPrompts(new PromptLoader(TestProps.defaults())), ChatOptions.builder().temperature(0.3).build());
        RepairLoop loop = new RepairLoop(client, new GreedyFallbackPlanner(), new PlanValidator(), TestProps.defaults());
        return new PlannerOrchestrator(weather, new RecommendationService(places, TestProps.defaults()),
                new EvidenceService(retriever), loop, new Assembler());
    }

    private static final String VALID = PlannerClientTest.json(PlannerFixtures.validDraft());
    private static final String UNKNOWN_ID = PlannerClientTest.json(
            PlannerFixtures.withDay(1, PlannerFixtures.item("danang-made-up", Slot.MORNING)));

    @Test
    void happyPathUsesTheModelDraft() {
        model.reply(VALID);

        PlannerOrchestrator.Result r = orchestrator(new NoWeatherProvider()).plan(PlannerFixtures.family());
        TripPlan plan = r.plan();

        assertThat(plan.meta().fallback()).isFalse();
        assertThat(plan.meta().repaired()).isFalse();
        assertThat(plan.meta().promptVersion()).isEqualTo("planner-v1");
        assertThat(plan.budget().totalVnd()).isEqualTo(PlannerFixtures.VALID_DRAFT_TOTAL);
        assertThat(plan.days().get(2).items().getFirst().placeId()).isEqualTo(Personas.BA_NA);
        assertThat(plan.warnings()).extracting(Warning::code).contains("WEATHER_UNAVAILABLE", "DAYTRIP_LUNCH")
                .last().isEqualTo("PRICE_ESTIMATE_DISCLAIMER");
        assertThat(r.stepMillis()).containsOnlyKeys("weather", "candidates", "retrieve", "llm", "assemble");
    }

    @Test
    void repairBranch() {
        model.reply(UNKNOWN_ID, VALID);
        TripPlan plan = orchestrator(new NoWeatherProvider()).plan(PlannerFixtures.family()).plan();
        assertThat(plan.meta().repaired()).isTrue();
        assertThat(plan.meta().repairs()).isEqualTo(1);
        assertThat(plan.meta().fallback()).isFalse();
    }

    @Test
    void fallbackBranchStillReturnsAValidPlan() {
        model.reply(UNKNOWN_ID, UNKNOWN_ID, UNKNOWN_ID);
        PlannerOrchestrator.Result r = orchestrator(new NoWeatherProvider()).plan(PlannerFixtures.family());
        assertThat(r.plan().meta().fallback()).isTrue();
        assertThat(r.outcome().report().isValid()).isTrue();
        assertThat(r.plan().days()).hasSize(3).allSatisfy(d -> assertThat(d.items()).isNotEmpty());
        assertThat(r.plan().warnings()).extracting(Warning::code).contains("FALLBACK_USED");
        assertThat(r.plan().budget().overBudget()).isFalse();
    }

    @Test
    void brokenWeatherProviderDoesNotBlockPlanning() {
        model.reply(VALID);
        WeatherProvider broken = (d, dates) -> {
            throw new IllegalStateException("open-meteo down");
        };
        TripPlan plan = orchestrator(broken).plan(PlannerFixtures.family()).plan();
        assertThat(plan.warnings()).extracting(Warning::code).contains("WEATHER_UNAVAILABLE");
        assertThat(plan.meta().fallback()).isFalse();
    }

    @Test
    void modelOutageFallsBack() {
        model.fail(new RuntimeException("quota"));
        TripPlan plan = orchestrator(new NoWeatherProvider()).plan(Personas.couple()).plan();
        assertThat(plan.meta().fallback()).isTrue();
        assertThat(plan.days()).hasSize(2);
    }
}
