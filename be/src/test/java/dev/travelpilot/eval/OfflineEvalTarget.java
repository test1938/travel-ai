package dev.travelpilot.eval;

import dev.travelpilot.common.ai.AiCallLogger;
import dev.travelpilot.conversation.ChatService;
import dev.travelpilot.integration.weather.NoWeatherProvider;
import dev.travelpilot.planner.OfflinePlanning;
import dev.travelpilot.planner.PlannerOrchestrator;
import dev.travelpilot.support.StubChatModel;
import dev.travelpilot.trip.TripPlan;
import dev.travelpilot.trip.TripRequest;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.UUID;

/**
 * The planning pipeline without any model: every planner call fails, so each plan comes from candidates + the
 * greedy fallback. This measures the safety net (filters, scoring, fallback, budget) and runs anywhere, without
 * Docker or a key. Chat cases need a model and are skipped.
 */
public class OfflineEvalTarget implements EvalTarget {

    private final PlannerOrchestrator orchestrator;

    public OfflineEvalTarget() {
        StubChatModel noModel = new StubChatModel();   // no reply queued: every call fails -> fallback
        this.orchestrator = OfflinePlanning.orchestrator(noModel, OfflinePlanning.places(), new NoWeatherProvider(),
                AiCallLogger.none(), new SimpleMeterRegistry());
    }

    @Override
    public String mode() {
        return "offline-fallback";
    }

    @Override
    public String model() {
        return "none (greedy fallback)";
    }

    @Override
    public boolean supports(EvalCase.Category category) {
        return !category.chats();
    }

    @Override
    public PlannerOrchestrator.Result plan(TripRequest request) {
        return orchestrator.plan(request);
    }

    @Override
    public TripPlan createTrip(String guest, TripRequest request) {
        throw new UnsupportedOperationException("chat cases need a model");
    }

    @Override
    public TripPlan current(UUID tripId, String guest) {
        throw new UnsupportedOperationException("chat cases need a model");
    }

    @Override
    public ChatService.ChatReply chat(UUID tripId, String guest, String message) {
        throw new UnsupportedOperationException("chat cases need a model");
    }

    @Override
    public Tokens tokens() {
        return Tokens.ZERO;
    }
}
