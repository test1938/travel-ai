package dev.travelpilot.eval;

import dev.travelpilot.common.domain.Destination;
import dev.travelpilot.common.domain.Pace;
import dev.travelpilot.common.domain.PlaceTag;
import dev.travelpilot.conversation.Intent;
import dev.travelpilot.trip.TripRequest;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * One planner/chat eval case from be/eval/cases.json. HAPPY and HARD cases plan a trip; MODIFY, ASK and ATTACK
 * cases plan a trip and then send {@code message} in the trip chat. Dates are relative ({@code startInDays}) so
 * the file never goes stale.
 */
public record EvalCase(String id, Category category, String description, RequestSpec request, String message,
        Expect expect) {

    public static final Path FILE = Path.of("eval", "cases.json");

    public enum Category {
        HAPPY, HARD, MODIFY, ASK, ATTACK;

        /** Categories that send a chat message after planning. */
        public boolean chats() {
            return this == MODIFY || this == ASK || this == ATTACK;
        }
    }

    public record RequestSpec(Destination destination, int startInDays, int days, int adults,
            List<Integer> childrenAges, long budgetVnd, Set<PlaceTag> preferences, Pace pace, List<String> notes) {

        public TripRequest toRequest(LocalDate today) {
            return new TripRequest(destination, today.plusDays(startInDays), days, adults, childrenAges, budgetVnd,
                    preferences, pace, notes);
        }
    }

    /**
     * Expectations; null means "not checked". {@code maxBudgetVnd} overrides the request budget (after a change),
     * {@code lockedDays} must be identical to version 1, {@code indoorDay} must only have indoor activities.
     */
    public record Expect(
            List<String> mustIncludeAnyOf,
            List<String> mustNotInclude,
            Boolean childSafe,
            Boolean withinBudget,
            Long maxBudgetVnd,
            Integer maxActivitiesPerDay,
            List<Integer> lockedDays,
            Integer indoorDay,
            Intent intent,
            Boolean newVersion,
            Boolean planUnchanged,
            Boolean refusal,
            Boolean hasSources,
            List<String> mustMentionAnyOf,
            Long maxLatencyMs,
            Integer maxTokens) {

        public Expect {
            mustIncludeAnyOf = mustIncludeAnyOf == null ? List.of() : List.copyOf(mustIncludeAnyOf);
            mustNotInclude = mustNotInclude == null ? List.of() : List.copyOf(mustNotInclude);
            lockedDays = lockedDays == null ? List.of() : List.copyOf(lockedDays);
            mustMentionAnyOf = mustMentionAnyOf == null ? List.of() : List.copyOf(mustMentionAnyOf);
        }
    }

    public static List<EvalCase> load() {
        return load(FILE);
    }

    public static List<EvalCase> load(Path file) {
        try {
            return JsonMapper.builder().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build()
                    .readValue(Files.readString(file), new TypeReference<List<EvalCase>>() {});
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read " + file.toAbsolutePath(), e);
        }
    }
}
