package dev.travelpilot.planner;

import dev.travelpilot.catalog.Place;
import dev.travelpilot.common.Vnd;
import dev.travelpilot.common.domain.PlaceType;
import dev.travelpilot.common.domain.Slot;
import dev.travelpilot.knowledge.Source;
import dev.travelpilot.planner.TripPlanDraft.DraftDay;
import dev.travelpilot.planner.TripPlanDraft.DraftItem;
import dev.travelpilot.recommendation.ScoreBreakdown;
import dev.travelpilot.recommendation.ScoredPlace;
import dev.travelpilot.trip.TripPlan;
import dev.travelpilot.trip.TripPlan.DayPlan;
import dev.travelpilot.trip.TripPlan.PlanItem;
import dev.travelpilot.trip.TripPlan.PlanMeta;
import dev.travelpilot.trip.TripRequest;
import dev.travelpilot.trip.Warning;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Draft -> TripPlan. Names, prices, durations and times come from the catalog and {@link DaySchedule}; the
 * budget from {@link BudgetCalculator}. Unknown evidence ids are dropped, empty or long reasons are fixed.
 */
@Component
public class Assembler {

    static final String DEFAULT_REASON = "Phù hợp với yêu cầu chuyến đi của bạn.";
    static final String DISCLAIMER =
            "Giá là ước tính, chưa gồm khách sạn và di chuyển; hãy kiểm tra lại trước khi đi.";
    /** Soft violations the assembler already fixed; not shown to the user. */
    private static final Set<String> SILENT_CODES = Set.of("BAD_SOURCE", "BAD_REASON");

    public TripPlan assemble(TripPlanDraft draft, PlanContext ctx, List<Violation> softViolations,
            List<Warning> extraWarnings, PlanMeta meta) {
        TripRequest req = ctx.request();
        Map<String, Place> places = ctx.knownPlaces();
        Map<String, ScoredPlace> scored = ctx.candidatesById();
        Set<String> evidenceIds = ctx.evidenceIds();

        List<DayPlan> days = new ArrayList<>();
        List<PlanItem> allItems = new ArrayList<>();
        Set<String> usedEvidence = new LinkedHashSet<>();
        List<Warning> warnings = new ArrayList<>(extraWarnings);
        warnings.addAll(ctx.candidates().warnings());

        for (DraftDay day : draft.days()) {
            List<PlanItem> items = new ArrayList<>();
            for (DaySchedule.Timed t : DaySchedule.schedule(day, places)) {
                Place p = t.place();
                List<String> sources = t.item().sourceIds().stream().filter(evidenceIds::contains).distinct().toList();
                usedEvidence.addAll(sources);
                ScoredPlace s = scored.get(p.id());
                ScoreBreakdown score = s == null ? null : s.score();
                items.add(new PlanItem(p.id(), p.name(), p.type(), t.item().slot(), t.start(), t.end(),
                        p.durationMin(), req.groupCost(p), p.isPriceEstimated(), p.priceNote(), p.verifyAt(),
                        p.indoor(), p.bestTime(), reason(t.item()), sources, score, p.lat(), p.lon()));
            }
            if (startsWithLongMorningVisit(day, places)) {
                warnings.add(new Warning("DAYTRIP_LUNCH",
                        "Ngày đi xa: ăn trưa tại điểm đến, chi phí bữa trưa chưa tính vào ngân sách.",
                        day.dayIndex(), null));
            }
            allItems.addAll(items);
            days.add(new DayPlan(day.dayIndex(), ctx.date(day.dayIndex()),
                    ctx.forecast().days().get(ctx.date(day.dayIndex())), items));
        }

        TripPlan.Budget budget = BudgetCalculator.total(allItems, req.budgetVnd());
        softViolations.stream().filter(v -> !SILENT_CODES.contains(v.code()))
                .map(v -> new Warning(v.code(), v.message(), v.dayIndex(), v.placeId()))
                .forEach(warnings::add);
        if (budget.overBudget()) {
            warnings.add(Warning.of("OVER_BUDGET", "Tổng chi phí vượt ngân sách %s."
                    .formatted(Vnd.format(budget.totalVnd() - budget.limitVnd()))));
        }
        warnings.add(Warning.of("PRICE_ESTIMATE_DISCLAIMER", DISCLAIMER));

        return new TripPlan(null, 0, text(draft.title(), defaultTitle(req)), text(draft.summary(), ""), days,
                budget, dedupe(warnings), sources(ctx, usedEvidence), meta);
    }

    private static String reason(DraftItem item) {
        String r = item.reason() == null ? "" : item.reason().strip();
        if (r.isEmpty()) {
            return DEFAULT_REASON;
        }
        return r.length() <= PlanRules.MAX_REASON_CHARS ? r : r.substring(0, PlanRules.MAX_REASON_CHARS - 1) + "…";
    }

    private static boolean startsWithLongMorningVisit(DraftDay day, Map<String, Place> places) {
        return day.items().stream().anyMatch(i -> i.slot() == Slot.MORNING && places.containsKey(i.placeId())
                && places.get(i.placeId()).type() == PlaceType.ACTIVITY && DaySchedule.isLong(places.get(i.placeId())));
    }

    /** Evidence cited by the plan, in ev-number order. */
    private static List<Source> sources(PlanContext ctx, Set<String> used) {
        return ctx.evidence().stream()
                .filter(e -> used.contains(e.evId()))
                .sorted(Comparator.comparingInt(e -> Integer.parseInt(e.evId().substring(3))))
                .map(PlanEvidence::toSource)
                .toList();
    }

    private static List<Warning> dedupe(List<Warning> warnings) {
        Map<String, Warning> unique = new LinkedHashMap<>();
        warnings.forEach(w -> unique.putIfAbsent(w.code() + "|" + w.dayIndex() + "|" + w.placeId(), w));
        return List.copyOf(unique.values());
    }

    private static String text(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.strip();
    }

    private static String defaultTitle(TripRequest req) {
        return "Lịch trình " + req.days() + " ngày ở " + req.destination().displayName();
    }
}
