package dev.travelpilot.conversation;

import dev.travelpilot.common.domain.Slot;
import dev.travelpilot.planner.PlannerPromptFormatter;
import dev.travelpilot.trip.TripPlan;
import dev.travelpilot.trip.TripPlan.PlanItem;
import dev.travelpilot.trip.TripRequest;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Bounded memory for one chat turn: the CURRENT constraints and plan come from the database (so the model can
 * never "forget" the budget), plus only the last few messages of the conversation.
 */
public record ChatContext(UUID tripId, TripRequest request, TripPlan plan, List<ChatMessage> window) {

    public ChatContext {
        window = List.copyOf(window);
    }

    /** Current request as text (destination, dates, people, budget, preferences, pace, notes). */
    public String tripText() {
        return PlannerPromptFormatter.request(request);
    }

    /** "Day 1 (2026-11-12): MORNING Bãi biển Mỹ Khê [id] ..." one line per day; ids let the model refer to places. */
    public String planText() {
        return plan.days().stream()
                .map(d -> "Day " + d.dayIndex() + " (" + d.date() + "): " + d.items().stream()
                        .map(ChatContext::describe).collect(Collectors.joining("; ")))
                .collect(Collectors.joining("\n"));
    }

    public String historyText() {
        if (window.isEmpty()) {
            return "(none)";
        }
        return window.stream()
                .map(m -> m.role() + ": " + m.content().replace('\n', ' '))
                .collect(Collectors.joining("\n"));
    }

    /** Base parameters shared by the chat prompts. */
    public Map<String, Object> promptParams(String message) {
        return Map.of("trip", tripText(), "plan", planText(), "history", historyText(), "message", message);
    }

    private static String describe(PlanItem i) {
        return slot(i.slot()) + " " + i.name() + " [" + i.placeId() + "]";
    }

    private static String slot(Slot s) {
        return s == null ? "?" : s.name();
    }
}
