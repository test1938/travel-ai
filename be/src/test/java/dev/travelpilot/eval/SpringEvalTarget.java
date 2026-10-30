package dev.travelpilot.eval;

import dev.travelpilot.conversation.ChatService;
import dev.travelpilot.planner.PlannerOrchestrator;
import dev.travelpilot.trip.TripPlan;
import dev.travelpilot.trip.TripRequest;
import dev.travelpilot.trip.TripService;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.UUID;

/** The real application: Spring beans, Postgres/pgvector and the configured Gemini model. */
public class SpringEvalTarget implements EvalTarget {

    private final PlannerOrchestrator orchestrator;
    private final TripService trips;
    private final ChatService chat;
    private final MeterRegistry meters;
    private final String model;

    public SpringEvalTarget(PlannerOrchestrator orchestrator, TripService trips, ChatService chat,
            MeterRegistry meters, String model) {
        this.orchestrator = orchestrator;
        this.trips = trips;
        this.chat = chat;
        this.meters = meters;
        this.model = model;
    }

    @Override
    public String mode() {
        return model;
    }

    @Override
    public String model() {
        return model;
    }

    @Override
    public boolean supports(EvalCase.Category category) {
        return true;
    }

    @Override
    public PlannerOrchestrator.Result plan(TripRequest request) {
        return orchestrator.plan(request);
    }

    @Override
    public TripPlan createTrip(String guest, TripRequest request) {
        return trips.create(guest, request);
    }

    @Override
    public TripPlan current(UUID tripId, String guest) {
        return trips.get(tripId, guest);
    }

    @Override
    public ChatService.ChatReply chat(UUID tripId, String guest, String message) {
        return chat.handle(tripId, guest, message);
    }

    @Override
    public Tokens tokens() {
        return Tokens.of(meters);
    }
}
