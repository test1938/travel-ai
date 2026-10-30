package dev.travelpilot.planner;

import dev.travelpilot.catalog.OpeningHours;
import dev.travelpilot.catalog.Place;
import dev.travelpilot.common.domain.PlaceTag;
import dev.travelpilot.common.domain.PlaceType;
import dev.travelpilot.common.domain.Slot;
import dev.travelpilot.planner.TripPlanDraft.DraftDay;
import dev.travelpilot.planner.TripPlanDraft.DraftItem;
import dev.travelpilot.recommendation.ScoredPlace;
import dev.travelpilot.trip.TripRequest;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Deterministic planner without the LLM (ADR-007): fills each day slot by slot with the best affordable
 * candidate that fits the slot, the pace and the budget (keeping money for the remaining meals), prefers indoor
 * places on rainy days, then adds lunch and dinner. Uses {@link DaySchedule} rules, so its output passes the
 * validator whenever the candidates make that possible.
 */
@Component
public class GreedyFallbackPlanner implements DraftPlanner {

    public static final String VERSION = "greedy-v1";
    static final double RAIN_INDOOR_BOOST = 0.2;
    /** Evening activities after dinner must end by 22:30, so the fallback uses less than the 180-minute cap. */
    static final int EVENING_ACTIVITY_MINUTES = 120;

    private static final Map<PlaceTag, String> LABELS = Map.ofEntries(
            Map.entry(PlaceTag.BEACH, "biển"), Map.entry(PlaceTag.NATURE, "thiên nhiên"),
            Map.entry(PlaceTag.CULTURE, "văn hóa"), Map.entry(PlaceTag.HISTORY, "lịch sử"),
            Map.entry(PlaceTag.MUSEUM, "bảo tàng"), Map.entry(PlaceTag.FOOD, "ẩm thực"),
            Map.entry(PlaceTag.STREET_FOOD, "ẩm thực đường phố"), Map.entry(PlaceTag.SEAFOOD, "hải sản"),
            Map.entry(PlaceTag.PHOTO, "chụp ảnh"), Map.entry(PlaceTag.FAMILY, "gia đình"),
            Map.entry(PlaceTag.THEME_PARK, "công viên giải trí"), Map.entry(PlaceTag.NIGHT, "đi chơi buổi tối"),
            Map.entry(PlaceTag.SHOPPING, "mua sắm"), Map.entry(PlaceTag.RELAX, "thư giãn"),
            Map.entry(PlaceTag.ADVENTURE, "trải nghiệm"));

    @Override
    public PlanAttempt plan(PlanContext ctx) {
        return PlanAttempt.ofDraft(buildDraft(ctx), VERSION, "none");
    }

    TripPlanDraft buildDraft(PlanContext ctx) {
        TripRequest req = ctx.request();
        Map<String, Place> known = ctx.knownPlaces();
        long cheapestMeal = ctx.candidates().food().stream()
                .mapToLong(f -> req.groupCost(f.place())).min().orElse(0);

        Set<String> usedActivities = new HashSet<>();
        Map<String, Integer> mealUses = new HashMap<>();
        long budgetLeft = req.budgetVnd();
        for (DraftDay locked : ctx.lockedDays().values()) {
            for (DraftItem item : locked.items()) {
                Place p = known.get(item.placeId());
                if (p != null) {
                    budgetLeft -= req.groupCost(p);
                    if (p.type() == PlaceType.ACTIVITY) {
                        usedActivities.add(p.id());
                    } else {
                        mealUses.merge(p.id(), 1, Integer::sum);
                    }
                }
            }
        }

        List<DraftDay> days = new ArrayList<>();
        int freeDaysLeft = req.days() - ctx.lockedDays().size();
        for (int dayIndex = 1; dayIndex <= req.days(); dayIndex++) {
            if (ctx.isLocked(dayIndex)) {
                days.add(ctx.lockedDays().get(dayIndex));
                continue;
            }
            freeDaysLeft--;
            DayBuilder day = new DayBuilder(ctx, dayIndex, usedActivities, mealUses);
            long mealReserve = cheapestMeal * 2 * (freeDaysLeft + 1);
            budgetLeft -= day.fillActivities(budgetLeft - mealReserve);
            budgetLeft -= day.addMeals(budgetLeft - cheapestMeal * 2 * freeDaysLeft);
            days.add(day.build());
        }
        return new TripPlanDraft(
                "Lịch trình " + req.days() + " ngày ở " + req.destination().displayName(),
                "Lịch được xếp tự động theo điểm phù hợp, ngân sách và thời gian của từng buổi.",
                days);
    }

    /** Mutable state while filling one day. */
    private static final class DayBuilder {
        private final PlanContext ctx;
        private final TripRequest req;
        private final int dayIndex;
        private final LocalDate date;
        private final boolean rainy;
        private final Set<String> usedActivities;
        private final Map<String, Integer> mealUses;
        private final Map<Slot, List<DraftItem>> slots = new EnumMap<>(Slot.class);
        private int activities;
        private boolean afternoonBlocked;
        private boolean eveningActivitiesBlocked;

        DayBuilder(PlanContext ctx, int dayIndex, Set<String> usedActivities, Map<String, Integer> mealUses) {
            this.ctx = ctx;
            this.req = ctx.request();
            this.dayIndex = dayIndex;
            this.date = ctx.date(dayIndex);
            this.rainy = ctx.isRainy(dayIndex);
            this.usedActivities = usedActivities;
            this.mealUses = mealUses;
            for (Slot s : Slot.values()) {
                slots.put(s, new ArrayList<>());
            }
        }

        /** Returns the money spent. On rainy days the first pass only considers indoor places. */
        long fillActivities(long spendable) {
            List<ScoredPlace> ranked = ranked();
            long spent = rainy
                    ? pass(ranked.stream().filter(c -> c.place().indoor()).toList(), spendable, 0)
                    : pass(ranked, spendable, 0);
            if (rainy && activities == 0) {
                spent = pass(ranked, spendable, spent);
            }
            if (activities == 0) {
                spent += addAnyActivity(spendable - spent);
            }
            return spent;
        }

        private long pass(List<ScoredPlace> ranked, long spendable, long spentSoFar) {
            long spent = spentSoFar;
            for (Slot slot : Slot.values()) {
                if ((slot == Slot.AFTERNOON && afternoonBlocked) || (slot == Slot.EVENING && eveningActivitiesBlocked)) {
                    continue;
                }
                for (ScoredPlace c : ranked) {
                    if (activities >= req.pace().maxActivitiesPerDay()) {
                        return spent;
                    }
                    long cost = req.groupCost(c.place());
                    if (cost <= spendable - spent && fits(c.place(), slot, true)) {
                        add(c, slot);
                        spent += cost;
                        if (DaySchedule.isLong(c.place())) {
                            break;   // a long visit is alone in its slot
                        }
                    }
                }
            }
            return spent;
        }

        /** Last resort for an empty day: ignore best time, keep every hard rule. */
        private long addAnyActivity(long spendable) {
            for (ScoredPlace c : ranked()) {
                long cost = req.groupCost(c.place());
                if (cost > spendable) {
                    continue;
                }
                for (Slot slot : Slot.values()) {
                    if (fits(c.place(), slot, false)) {
                        add(c, slot);
                        return cost;
                    }
                }
            }
            return 0;
        }

        private boolean fits(Place p, Slot slot, boolean respectBestTime) {
            if (usedActivities.contains(p.id()) || (respectBestTime && !p.bestTime().fits(slot))) {
                return false;
            }
            OpeningHours hours = OpeningHours.parse(p.openingHours());
            if (hours.isOpen(date) == OpeningHours.Status.CLOSED) {
                return false;
            }
            boolean slotHasActivity = slots.get(slot).stream().anyMatch(i -> isActivity(i.placeId()));
            if (DaySchedule.isLong(p)) {
                return slot != Slot.EVENING && !slotHasActivity
                        && (slot != Slot.MORNING || slots.get(Slot.AFTERNOON).isEmpty())
                        && endsInTime(p, slot);
            }
            if (slotHasActivity && slots.get(slot).stream().anyMatch(i -> DaySchedule.isLong(place(i.placeId())))) {
                return false;
            }
            int capacity = slot == Slot.EVENING ? EVENING_ACTIVITY_MINUTES : DaySchedule.CAPACITY.get(slot);
            List<Place> withCandidate = new ArrayList<>(slots.get(slot).stream()
                    .map(i -> place(i.placeId())).filter(x -> x.type() == PlaceType.ACTIVITY).toList());
            withCandidate.add(p);
            // Same measure as the validator: durations plus travel between activities.
            return DaySchedule.activityMinutes(withCandidate) <= capacity && endsInTime(p, slot);
        }

        /**
         * Simulates the whole day with the extra item using the shared DaySchedule. Adding an item can shift the
         * items after it (e.g. lunch pushes afternoon visits back), so every visit is checked: the day must end
         * by 22:30 and no place may be closed at the time it would be visited.
         */
        private boolean endsInTime(Place p, Slot slot) {
            List<DraftItem> items = new ArrayList<>();
            for (Slot s : Slot.values()) {
                items.addAll(slots.get(s));
                if (s == slot) {
                    items.add(new DraftItem(p.id(), slot, "", List.of()));
                }
            }
            List<DaySchedule.Timed> timeline = DaySchedule.schedule(new DraftDay(dayIndex, items), ctx.knownPlaces());
            if (!timeline.isEmpty() && timeline.getLast().endMin() > DaySchedule.DAY_END) {
                return false;
            }
            return timeline.stream().noneMatch(t -> OpeningHours.parse(t.place().openingHours())
                    .isOpen(date, t.start(), t.end()) == OpeningHours.Status.CLOSED);
        }

        private void add(ScoredPlace c, Slot slot) {
            Place p = c.place();
            slots.get(slot).add(new DraftItem(p.id(), slot, activityReason(c), List.of()));
            usedActivities.add(p.id());
            activities++;
            if (DaySchedule.isLong(p)) {
                if (slot == Slot.MORNING) {
                    afternoonBlocked = true;
                } else {
                    eveningActivitiesBlocked = true;
                }
            }
        }

        /** Lunch (unless a long morning visit) and dinner; prefers restaurants not used yet. Returns money spent. */
        long addMeals(long spendable) {
            long spent = 0;
            List<Slot> mealSlots = afternoonBlocked ? List.of(Slot.EVENING) : List.of(Slot.AFTERNOON, Slot.EVENING);
            for (Slot slot : mealSlots) {
                ScoredPlace meal = pickMeal(spendable - spent, slot);
                if (meal != null) {
                    slots.get(slot).add(new DraftItem(meal.id(), slot, mealReason(meal, slot), List.of()));
                    mealUses.merge(meal.id(), 1, Integer::sum);
                    spent += req.groupCost(meal.place());
                }
            }
            return spent;
        }

        private ScoredPlace pickMeal(long spendable, Slot slot) {
            return ctx.candidates().food().stream()
                    .filter(f -> req.groupCost(f.place()) <= spendable)
                    .filter(f -> endsInTime(f.place(), slot))
                    .filter(f -> OpeningHours.parse(f.place().openingHours()).isOpen(date) != OpeningHours.Status.CLOSED)
                    // Cafés and dessert shops are not meals unless nothing else is left.
                    .min(Comparator.<ScoredPlace>comparingInt(f -> f.place().hasTag(PlaceTag.CAFE) ? 1 : 0)
                            .thenComparingInt(f -> mealUses.getOrDefault(f.id(), 0))
                            .thenComparing(Comparator.comparingDouble((ScoredPlace f) -> f.score().total()).reversed()))
                    .orElse(null);
        }

        /** Must-see places first (best match of each preference), then by score adjusted for rain. */
        private List<ScoredPlace> ranked() {
            Set<String> mustSee = mustSee();
            return ctx.candidates().activities().stream()
                    .sorted(Comparator.<ScoredPlace>comparingInt(c -> mustSee.contains(c.id()) ? 0 : 1)
                            .thenComparing(Comparator.comparingDouble(this::adjustedScore).reversed())
                            .thenComparing(ScoredPlace::id))
                    .toList();
        }

        /** For every preference not yet covered by a planned activity, its best-scoring candidate. */
        private Set<String> mustSee() {
            Set<String> ids = new HashSet<>();
            for (PlaceTag pref : req.preferences()) {
                boolean covered = usedActivities.stream()
                        .map(id -> ctx.knownPlaces().get(id))
                        .anyMatch(p -> p != null && p.hasTag(pref));
                if (!covered) {
                    ctx.candidates().activities().stream()
                            .filter(c -> c.place().hasTag(pref) && !usedActivities.contains(c.id()))
                            .findFirst().ifPresent(c -> ids.add(c.id()));
                }
            }
            return ids;
        }

        private double adjustedScore(ScoredPlace c) {
            if (!rainy) {
                return c.score().total();
            }
            return c.score().total() + (c.place().indoor() ? RAIN_INDOOR_BOOST : -RAIN_INDOOR_BOOST);
        }

        private String activityReason(ScoredPlace c) {
            String score = String.format(Locale.ROOT, " (điểm %.2f)", c.score().total());
            if (rainy && c.place().indoor()) {
                return "Hoạt động trong nhà, hợp ngày dự báo mưa" + score;
            }
            return req.preferences().stream().filter(c.place()::hasTag).map(LABELS::get).findFirst()
                    .map(label -> "Phù hợp sở thích " + label + score)
                    .orElse("Được xếp hạng cao cho nhóm của bạn" + score);
        }

        private static String mealReason(ScoredPlace meal, Slot slot) {
            return (slot == Slot.AFTERNOON ? "Bữa trưa" : "Bữa tối") + ": " + meal.place().name()
                    + String.format(Locale.ROOT, " (điểm %.2f)", meal.score().total());
        }

        private boolean isActivity(String placeId) {
            Place p = place(placeId);
            return p != null && p.type() == PlaceType.ACTIVITY;
        }

        private Place place(String placeId) {
            return ctx.knownPlaces().get(placeId);
        }

        DraftDay build() {
            List<DraftItem> items = new ArrayList<>();
            for (Slot s : Slot.values()) {
                items.addAll(slots.get(s));
            }
            return new DraftDay(dayIndex, items);
        }
    }
}
