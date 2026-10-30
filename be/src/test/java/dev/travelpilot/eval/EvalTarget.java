package dev.travelpilot.eval;

import dev.travelpilot.common.metrics.TpMetrics;
import dev.travelpilot.conversation.ChatService;
import dev.travelpilot.planner.PlannerOrchestrator;
import dev.travelpilot.trip.TripPlan;
import dev.travelpilot.trip.TripRequest;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.UUID;

/**
 * The system under evaluation (port): the real Spring application with Gemini, or the offline pipeline without a
 * model. The engine only talks to this interface.
 */
public interface EvalTarget {

    /** Operation name of judge calls; their tokens are not counted as cost of the system. */
    String JUDGE_OPERATION = "JUDGE";

    /** Short label for the report, e.g. "gemini-2.5-flash" or "offline-fallback". */
    String mode();

    /** Model name for the report header. */
    String model();

    boolean supports(EvalCase.Category category);

    PlannerOrchestrator.Result plan(TripRequest request);

    /** Plans and saves a trip (version 1) owned by {@code guest}. */
    TripPlan createTrip(String guest, TripRequest request);

    TripPlan current(UUID tripId, String guest);

    ChatService.ChatReply chat(UUID tripId, String guest, String message);

    /** Cumulative model tokens so far (judge calls excluded). */
    Tokens tokens();

    record Tokens(long in, long out) {

        public static final Tokens ZERO = new Tokens(0, 0);

        public Tokens minus(Tokens other) {
            return new Tokens(in - other.in, out - other.out);
        }

        public long total() {
            return in + out;
        }

        /** Sums tp.llm.tokens over all operations except the judge. */
        public static Tokens of(MeterRegistry meters) {
            long in = 0;
            long out = 0;
            for (Counter counter : meters.find(TpMetrics.LLM_TOKENS).counters()) {
                if (JUDGE_OPERATION.equals(counter.getId().getTag("operation"))) {
                    continue;
                }
                long value = (long) counter.count();
                if ("input".equals(counter.getId().getTag("type"))) {
                    in += value;
                } else {
                    out += value;
                }
            }
            return new Tokens(in, out);
        }
    }
}
