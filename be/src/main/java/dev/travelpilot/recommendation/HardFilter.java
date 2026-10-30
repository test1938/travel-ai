package dev.travelpilot.recommendation;

import dev.travelpilot.catalog.OpeningHours;
import dev.travelpilot.catalog.Place;
import dev.travelpilot.trip.TripRequest;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;

/**
 * Removes places that must never be planned for this request (plan/03-ai-pipeline.md §1). Pure: no Spring,
 * no I/O. Every rejection carries a {@link FilterReason}.
 */
public final class HardFilter {

    /** Share of the total budget a single item may cost for the whole group. */
    public static final double DEFAULT_MAX_COST_SHARE = 0.5;

    /** @param indoorOnly used when re-planning a rainy day. */
    public record Options(double maxCostShare, Set<String> excludeIds, boolean indoorOnly) {

        public static Options defaults() {
            return new Options(DEFAULT_MAX_COST_SHARE, Set.of(), false);
        }

        public Options withMaxCostShare(double share) {
            return new Options(share, excludeIds, indoorOnly);
        }

        public Options withExcludeIds(Set<String> ids) {
            return new Options(maxCostShare, Set.copyOf(ids), indoorOnly);
        }

        public Options withIndoorOnly(boolean indoor) {
            return new Options(maxCostShare, excludeIds, indoor);
        }
    }

    public record Result(List<Place> kept, Map<String, FilterReason> rejected) {

        public Result {
            kept = List.copyOf(kept);
            rejected = Map.copyOf(rejected);
        }
    }

    private HardFilter() {}

    public static Result apply(List<Place> places, TripRequest req, Options options) {
        List<Place> kept = new ArrayList<>();
        Map<String, FilterReason> rejected = new LinkedHashMap<>();
        for (Place place : places) {
            Optional<FilterReason> reason = reasonToReject(place, req, options);
            if (reason.isPresent()) {
                rejected.put(place.id(), reason.get());
            } else {
                kept.add(place);
            }
        }
        return new Result(kept, rejected);
    }

    static Optional<FilterReason> reasonToReject(Place place, TripRequest req, Options options) {
        if (place.destination() != req.destination()) {
            return Optional.of(FilterReason.WRONG_DESTINATION);
        }
        if (options.excludeIds().contains(place.id())) {
            return Optional.of(FilterReason.EXCLUDED_BY_USER);
        }
        OptionalInt youngest = req.youngestChildAge();
        if (youngest.isPresent() && place.childMinAge() > youngest.getAsInt()) {
            return Optional.of(FilterReason.CHILD_TOO_YOUNG);
        }
        if (req.groupCost(place) > options.maxCostShare() * req.budgetVnd()) {
            return Optional.of(FilterReason.TOO_EXPENSIVE);
        }
        if (options.indoorOnly() && !place.indoor()) {
            return Optional.of(FilterReason.NOT_INDOOR);
        }
        if (closedOnEveryTripDate(place, req.dates())) {
            return Optional.of(FilterReason.CLOSED_ON_TRIP_DATES);
        }
        return Optional.empty();
    }

    /** UNKNOWN hours never reject: the validator only warns about them. */
    private static boolean closedOnEveryTripDate(Place place, List<LocalDate> dates) {
        OpeningHours hours = OpeningHours.parse(place.openingHours());
        return dates.stream().allMatch(d -> hours.isOpen(d) == OpeningHours.Status.CLOSED);
    }
}
