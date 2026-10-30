package dev.travelpilot.conversation;

import dev.travelpilot.catalog.PlaceRepository;
import dev.travelpilot.common.ai.AiCallLogger;
import dev.travelpilot.common.ai.LlmGateway;
import dev.travelpilot.common.ai.PromptLoader;
import dev.travelpilot.common.ai.StructuredLlm;
import dev.travelpilot.common.domain.Destination;
import dev.travelpilot.integration.inventory.MockInventoryProvider;
import dev.travelpilot.integration.weather.DayWeather;
import dev.travelpilot.integration.weather.Forecast;
import dev.travelpilot.integration.weather.WeatherProvider;
import dev.travelpilot.planner.Assembler;
import dev.travelpilot.planner.PlanValidator;
import dev.travelpilot.planner.PlannerFixtures;
import dev.travelpilot.support.StubChatModel;
import dev.travelpilot.support.TestProps;
import dev.travelpilot.tools.TravelTools;
import dev.travelpilot.trip.TripPlan;
import dev.travelpilot.trip.TripPlan.PlanMeta;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.ChatOptions;
import tools.jackson.databind.json.JsonMapper;

/** Family trip (version 1 = PlannerFixtures.validDraft) and helpers for chat tests. */
final class ConversationFixtures {

    static final UUID TRIP_ID = UUID.fromString("11111111-2222-3333-4444-555555555555");

    private ConversationFixtures() {}

    static TripPlan familyPlan() {
        var ctx = PlannerFixtures.context();
        var draft = PlannerFixtures.validDraft();
        return new Assembler().assemble(draft, ctx, new PlanValidator().validate(draft, ctx).soft(), List.of(),
                new PlanMeta("planner-v1", "stub", false, 0, false, 1000)).withIdentity(TRIP_ID, 1);
    }

    static ChatContext context(List<ChatMessage> window) {
        return new ChatContext(TRIP_ID, PlannerFixtures.family(), familyPlan(), window);
    }

    static ChatContext context() {
        return context(List.of());
    }

    static StructuredLlm llm(StubChatModel model) {
        return llm(model, AiCallLogger.none());
    }

    static StructuredLlm llm(StubChatModel model, AiCallLogger calls) {
        return new StructuredLlm(ChatClient.create(model),
                new LlmGateway(Executors.newVirtualThreadPerTaskExecutor(), TestProps.defaults(), calls));
    }

    static PromptLoader prompts() {
        return new PromptLoader(TestProps.defaults());
    }

    /** The day before the family trip starts, so "mai" (tomorrow) is trip day 1. */
    static final Clock CLOCK = Clock.fixed(PlannerFixtures.family().startDate().minusDays(1).atStartOfDay()
            .atZone(Destination.ZONE).toInstant(), Destination.ZONE);

    /** Real @Tool methods over the catalog, the given weather and the mock inventory. */
    static TravelTools tools(PlaceRepository places, WeatherProvider weather) {
        return new TravelTools(places, weather, new MockInventoryProvider(), CLOCK);
    }

    static ToolLoop toolLoop(StubChatModel model, PlaceRepository places, WeatherProvider weather) {
        return toolLoop(model, places, weather, AiCallLogger.none());
    }

    static ToolLoop toolLoop(StubChatModel model, PlaceRepository places, WeatherProvider weather,
            AiCallLogger calls) {
        return new ToolLoop(llm(model, calls), prompts(), ChatOptions.builder().temperature(0.0).build(),
                tools(places, weather), JsonMapper.builder().build(), CLOCK, calls);
    }

    /** Every requested day rainy (code 63, 80 %). */
    static WeatherProvider rainEveryDay() {
        return (dest, dates) -> new Forecast(dates.stream().collect(Collectors.toMap(d -> d,
                d -> new DayWeather(d, true, 23.0, 27.0, 63, "Mưa (80% mưa)"))));
    }
}
