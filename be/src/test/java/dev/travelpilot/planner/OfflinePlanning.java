package dev.travelpilot.planner;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.travelpilot.catalog.Place;
import dev.travelpilot.catalog.PlaceRepository;
import dev.travelpilot.common.ai.LlmGateway;
import dev.travelpilot.common.ai.PromptLoader;
import dev.travelpilot.common.domain.Destination;
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
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executors;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.vectorstore.SimpleVectorStore;

/** The real planning workflow wired offline: catalog in memory, SimpleVectorStore, stub chat model. */
public final class OfflinePlanning {

    private static Retriever retriever;

    private OfflinePlanning() {}

    /** PlaceRepository mock backed by the real catalog. */
    @SuppressWarnings("unchecked")
    public static PlaceRepository places() {
        PlaceRepository places = mock(PlaceRepository.class);
        for (Destination d : Destination.values()) {
            when(places.findByDestination(d)).thenReturn(Personas.placesOf(d));
        }
        when(places.findByIds(any())).thenAnswer(inv -> ((Collection<String>) inv.getArgument(0)).stream()
                .map(Personas.BY_ID::get).filter(p -> p != null).toList());
        when(places.findById(any())).thenAnswer(inv -> Optional.ofNullable(Personas.BY_ID.get(inv.getArgument(0))));
        return places;
    }

    public static synchronized Retriever retriever() {
        if (retriever == null) {
            SimpleVectorStore store = SimpleVectorStore.builder(new StubEmbeddingModel(64)).build();
            new IngestionService(new KnowledgeLoader(), store, TestProps.defaults()).ingestAll();
            retriever = new Retriever(store, TestProps.defaults());
        }
        return retriever;
    }

    public static PlannerOrchestrator orchestrator(StubChatModel model, PlaceRepository places) {
        return orchestrator(model, places, new NoWeatherProvider());
    }

    public static PlannerOrchestrator orchestrator(StubChatModel model, PlaceRepository places,
            WeatherProvider weather) {
        PlannerClient client = new PlannerClient(ChatClient.create(model),
                new LlmGateway(Executors.newVirtualThreadPerTaskExecutor(), TestProps.defaults()),
                new PlannerPrompts(new PromptLoader(TestProps.defaults())),
                ChatOptions.builder().temperature(0.3).build());
        RepairLoop loop = new RepairLoop(client, new GreedyFallbackPlanner(), new PlanValidator(), TestProps.defaults());
        return new PlannerOrchestrator(weather, new RecommendationService(places, TestProps.defaults()),
                new EvidenceService(retriever()), loop, new Assembler());
    }

    public static List<Place> catalog() {
        return Personas.CATALOG;
    }
}
