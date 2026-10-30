package dev.travelpilot.trip;

import dev.travelpilot.catalog.Place;
import dev.travelpilot.common.domain.Destination;
import dev.travelpilot.common.domain.Pace;
import dev.travelpilot.common.domain.PlaceTag;
import dev.travelpilot.security.InputGuard;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.OptionalInt;
import java.util.Set;
import java.util.stream.IntStream;

/**
 * What the traveler asked for. Constraints live here as data (not only in chat history) so later edits keep
 * them. {@code budgetVnd} covers tickets and planned meals only, not hotel or transport (ADR-009).
 */
public record TripRequest(
        @NotNull Destination destination,
        @NotNull LocalDate startDate,
        @Min(1) @Max(5) int days,
        @Min(1) @Max(10) int adults,
        @Size(max = 6) List<@NotNull @Min(0) @Max(17) Integer> childrenAges,
        @Positive @Max(1_000_000_000) long budgetVnd,
        @Size(max = 6) Set<PlaceTag> preferences,
        @NotNull Pace pace,
        @Size(max = 3) List<@NotNull @Size(max = 200) String> notes) {

    /** Tags offered as preferences on the form (plan/02-architecture.md). */
    public static final Set<PlaceTag> ALLOWED_PREFERENCES = EnumSet.of(
            PlaceTag.BEACH, PlaceTag.NATURE, PlaceTag.CULTURE, PlaceTag.HISTORY, PlaceTag.MUSEUM, PlaceTag.FOOD,
            PlaceTag.STREET_FOOD, PlaceTag.SEAFOOD, PlaceTag.PHOTO, PlaceTag.FAMILY, PlaceTag.THEME_PARK,
            PlaceTag.NIGHT, PlaceTag.SHOPPING, PlaceTag.RELAX, PlaceTag.ADVENTURE);

    public TripRequest {
        childrenAges = childrenAges == null ? List.of() : List.copyOf(childrenAges);
        preferences = preferences == null || preferences.isEmpty() ? Set.of() : Set.copyOf(preferences);
        // Notes go into the planner prompt: strip control and invisible characters (length checked by @Size).
        notes = notes == null ? List.of() : notes.stream().map(InputGuard::clean).toList();
    }

    public int children() {
        return childrenAges.size();
    }

    public boolean hasChildren() {
        return !childrenAges.isEmpty();
    }

    public OptionalInt youngestChildAge() {
        return childrenAges.stream().mapToInt(Integer::intValue).min();
    }

    public int people() {
        return adults + children();
    }

    public List<LocalDate> dates() {
        return IntStream.range(0, days).mapToObj(startDate::plusDays).toList();
    }

    public LocalDate dateOf(int dayIndex) {
        return startDate.plusDays(dayIndex - 1L);
    }

    /**
     * Cost of one place for the whole group: adult price x adults + child price x children old enough to go.
     * FOOD prices are per meal and person.
     */
    public long groupCost(Place place) {
        long kids = childrenAges.stream().filter(age -> age >= place.childMinAge()).count();
        return place.estCostVnd() * adults + place.childCostVnd() * kids;
    }

    public TripRequest withBudget(long newBudgetVnd) {
        return new TripRequest(destination, startDate, days, adults, childrenAges, newBudgetVnd, preferences, pace,
                notes);
    }
}
