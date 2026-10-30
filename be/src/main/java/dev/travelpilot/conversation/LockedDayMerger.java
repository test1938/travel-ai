package dev.travelpilot.conversation;

import dev.travelpilot.knowledge.Source;
import dev.travelpilot.planner.BudgetCalculator;
import dev.travelpilot.trip.TripPlan;
import dev.travelpilot.trip.TripPlan.DayPlan;
import dev.travelpilot.trip.TripPlan.PlanItem;
import dev.travelpilot.trip.Warning;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * After a partial re-plan, puts the untouched days back exactly as the traveler saw them (times, reasons and
 * citations included) and recomputes the budget. Citations of kept days are renamed "v{n}-ev-k" so they cannot
 * clash with the new evidence ids.
 */
public final class LockedDayMerger {

    private LockedDayMerger() {}

    public static TripPlan merge(TripPlan fresh, TripPlan previous, Set<Integer> lockedDays) {
        String prefix = "v" + previous.version() + "-";
        Map<String, Source> oldSources = previous.sources().stream()
                .collect(Collectors.toMap(Source::id, Function.identity(), (a, b) -> a));
        Map<Integer, DayPlan> oldDays = previous.days().stream()
                .collect(Collectors.toMap(DayPlan::dayIndex, Function.identity()));

        List<DayPlan> days = new ArrayList<>();
        Set<String> keptSourceIds = new LinkedHashSet<>();
        for (DayPlan day : fresh.days()) {
            DayPlan old = oldDays.get(day.dayIndex());
            if (lockedDays.contains(day.dayIndex()) && old != null) {
                old.items().forEach(i -> keptSourceIds.addAll(i.sourceIds()));
                days.add(new DayPlan(old.dayIndex(), old.date(), day.weather(),
                        old.items().stream().map(i -> renameSources(i, prefix)).toList()));
            } else {
                days.add(day);
            }
        }

        List<Source> sources = new ArrayList<>(fresh.sources());
        keptSourceIds.stream().map(oldSources::get).filter(s -> s != null)
                .map(s -> new Source(prefix + s.id(), s.docId(), s.title(), s.url(), s.license(), s.snippet()))
                .forEach(sources::add);

        List<PlanItem> items = days.stream().flatMap(d -> d.items().stream()).toList();
        TripPlan.Budget budget = BudgetCalculator.total(items, fresh.budget().limitVnd());
        List<Warning> warnings = new ArrayList<>(fresh.warnings().stream()
                .filter(w -> !w.code().equals("OVER_BUDGET")).toList());
        if (budget.overBudget()) {
            // Keep the price disclaimer last.
            int at = warnings.isEmpty() || !warnings.getLast().code().equals("PRICE_ESTIMATE_DISCLAIMER")
                    ? warnings.size() : warnings.size() - 1;
            warnings.add(at, Warning.of("OVER_BUDGET", "Tổng chi phí vượt ngân sách."));
        }
        return new TripPlan(fresh.tripId(), fresh.version(), fresh.title(), fresh.summary(), days, budget, warnings,
                sources, fresh.meta());
    }

    private static PlanItem renameSources(PlanItem i, String prefix) {
        return new PlanItem(i.placeId(), i.name(), i.type(), i.slot(), i.start(), i.end(), i.durationMin(),
                i.costVnd(), i.costEstimated(), i.priceNote(), i.verifyAt(), i.indoor(), i.bestTime(), i.reason(),
                i.sourceIds().stream().map(id -> prefix + id).toList(), i.score(), i.lat(), i.lon());
    }
}
