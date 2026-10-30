package dev.travelpilot.planner;

import dev.travelpilot.catalog.Place;
import dev.travelpilot.common.domain.PlaceType;
import dev.travelpilot.trip.TripPlan.Budget;
import dev.travelpilot.trip.TripPlan.PlanItem;
import dev.travelpilot.trip.TripRequest;
import java.util.Collection;
import java.util.EnumMap;
import java.util.Map;

/** Deterministic money: the LLM never adds prices (ADR-006). */
public final class BudgetCalculator {

    private BudgetCalculator() {}

    /** Adult price x adults + child price x children old enough; meals are per person. */
    public static long itemCost(Place place, TripRequest req) {
        return req.groupCost(place);
    }

    public static Budget total(Collection<PlanItem> items, long limitVnd) {
        Map<PlaceType, Long> byType = new EnumMap<>(PlaceType.class);
        for (PlaceType t : PlaceType.values()) {
            byType.put(t, 0L);
        }
        items.forEach(i -> byType.merge(i.type(), i.costVnd(), Long::sum));
        long total = byType.values().stream().mapToLong(Long::longValue).sum();
        return new Budget(total, limitVnd, byType, total > limitVnd);
    }

    /** Group cost of the given places (ids may repeat, e.g. the same restaurant twice). */
    public static long totalOf(Collection<Place> places, TripRequest req) {
        return places.stream().mapToLong(p -> itemCost(p, req)).sum();
    }
}
