package dev.travelpilot.conversation;

import dev.travelpilot.catalog.Place;
import dev.travelpilot.catalog.PlaceRepository;
import dev.travelpilot.planner.PlannerOrchestrator;
import dev.travelpilot.planner.RepairLoop;
import dev.travelpilot.planner.TripPlanDraft.DraftDay;
import dev.travelpilot.planner.TripPlanDraft.DraftItem;
import dev.travelpilot.recommendation.HardFilter;
import dev.travelpilot.trip.TripPlan;
import dev.travelpilot.trip.TripPlan.DayPlan;
import dev.travelpilot.trip.TripRepository;
import dev.travelpilot.trip.TripRequest;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Applies a {@link ChangeRequest} to a saved trip: keeps the days that are not affected (locked), re-plans the
 * rest through the normal workflow, puts the locked days back exactly, and saves version n+1.
 */
@Service
public class TripRevisionService {

    /** The new version and a human summary of what changed. */
    public record Revision(TripPlan plan, String changeSummary, TripRequest request) {}

    private final PlannerOrchestrator orchestrator;
    private final PlaceRepository places;
    private final TripRepository trips;
    private final TransactionTemplate tx;

    public TripRevisionService(PlannerOrchestrator orchestrator, PlaceRepository places, TripRepository trips,
            TransactionTemplate tx) {
        this.orchestrator = orchestrator;
        this.places = places;
        this.trips = trips;
        this.tx = tx;
    }

    public Revision revise(UUID tripId, TripRequest before, TripPlan current, ChangeRequest change) {
        TripRequest after = change.applyTo(before);
        Set<Integer> unlocked = daysToReplan(current, change, before);
        Map<Integer, DraftDay> locked = new LinkedHashMap<>();
        for (DayPlan day : current.days()) {
            if (!unlocked.contains(day.dayIndex())) {
                locked.put(day.dayIndex(), toDraft(day));
            }
        }
        Set<String> lockedIds = locked.values().stream().flatMap(d -> d.items().stream())
                .map(DraftItem::placeId).collect(Collectors.toSet());
        Map<String, Place> lockedPlaces = places.findByIds(lockedIds).stream()
                .collect(Collectors.toMap(Place::id, Function.identity()));

        HardFilter.Options options = HardFilter.Options.defaults()
                .withExcludeIds(Set.copyOf(change.excludePlaceIds()))
                .withIndoorOnly(change.wantsIndoor());
        PlannerOrchestrator.Result result =
                orchestrator.plan(after, locked, lockedPlaces, options, change.instruction());

        int version = current.version() + 1;
        TripPlan merged = LockedDayMerger.merge(result.plan().withIdentity(tripId, version), current, locked.keySet());
        List<String> removedNames = current.days().stream().flatMap(d -> d.items().stream())
                .filter(i -> change.excludePlaceIds().contains(i.placeId()))
                .map(TripPlan.PlanItem::name).distinct().toList();
        String summary = change.summary(before, removedNames);

        RepairLoop.Outcome o = result.outcome();
        TripRepository.VersionFacts facts = new TripRepository.VersionFacts(summary, o.promptVersion(), o.model(),
                o.tokensIn(), o.tokensOut(), merged.meta().latencyMs(), o.repaired(), o.fallback());
        tx.executeWithoutResult(status -> {
            trips.insertVersion(tripId, version, merged, after, facts);
            trips.updateCurrent(tripId, version, after);
        });
        return new Revision(merged, summary, after);
    }

    /**
     * Day-scoped change: only that day. Removing places: also every day containing them. Budget or preference
     * change, or an indoor wish without a day: the whole trip.
     */
    static Set<Integer> daysToReplan(TripPlan current, ChangeRequest change, TripRequest before) {
        Set<Integer> all = current.days().stream().map(DayPlan::dayIndex).collect(Collectors.toSet());
        boolean global = (change.newBudgetVnd() != null && change.newBudgetVnd() != before.budgetVnd())
                || !change.addPreferences().isEmpty() || !change.removePreferences().isEmpty()
                || (change.wantsIndoor() && !change.isDayScoped());
        if (global) {
            return all;
        }
        Set<Integer> days = new HashSet<>();
        if (change.isDayScoped()) {
            days.add(change.dayIndex());
        }
        for (DayPlan day : current.days()) {
            if (day.items().stream().anyMatch(i -> change.excludePlaceIds().contains(i.placeId()))) {
                days.add(day.dayIndex());
            }
        }
        return days.isEmpty() ? all : days;
    }

    private static DraftDay toDraft(DayPlan day) {
        // Old evidence ids mean nothing in the new context; LockedDayMerger restores the original citations.
        return new DraftDay(day.dayIndex(), day.items().stream()
                .map(i -> new DraftItem(i.placeId(), i.slot(), i.reason(), List.of())).toList());
    }
}
