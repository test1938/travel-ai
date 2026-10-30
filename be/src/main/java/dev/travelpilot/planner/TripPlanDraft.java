package dev.travelpilot.planner;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import dev.travelpilot.common.domain.Slot;
import java.util.List;

/**
 * The ONLY thing the LLM returns: which candidate ids go in which slot of which day, with a reason and the
 * evidence ids used. No names, prices or times: the backend fills those from the catalog (ADR-004).
 */
public record TripPlanDraft(
        @JsonPropertyDescription("Short Vietnamese title of the trip, at most 80 characters") String title,
        @JsonPropertyDescription("One or two Vietnamese sentences summarising the plan") String summary,
        @JsonPropertyDescription("Exactly one entry per trip day, dayIndex 1..N in order") List<DraftDay> days) {

    public TripPlanDraft {
        days = days == null ? List.of() : List.copyOf(days);
    }

    public record DraftDay(
            @JsonPropertyDescription("1-based day number") int dayIndex,
            @JsonPropertyDescription("Places of the day in visiting order") List<DraftItem> items) {

        public DraftDay {
            items = items == null ? List.of() : List.copyOf(items);
        }
    }

    public record DraftItem(
            @JsonPropertyDescription("Candidate id copied exactly from the candidates table") String placeId,
            @JsonPropertyDescription("MORNING, AFTERNOON or EVENING") Slot slot,
            @JsonPropertyDescription("Why this fits the traveler, in Vietnamese, at most 25 words") String reason,
            @JsonPropertyDescription("Evidence ids such as ev-3 that support the reason; empty if none")
            List<String> sourceIds) {

        public DraftItem {
            sourceIds = sourceIds == null ? List.of() : List.copyOf(sourceIds);
        }
    }
}
