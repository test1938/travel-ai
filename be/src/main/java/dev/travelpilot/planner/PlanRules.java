package dev.travelpilot.planner;

import dev.travelpilot.catalog.OpeningHours;
import dev.travelpilot.catalog.Place;
import dev.travelpilot.common.Vnd;
import dev.travelpilot.common.domain.PlaceType;
import dev.travelpilot.common.domain.Slot;
import dev.travelpilot.planner.TripPlanDraft.DraftDay;
import dev.travelpilot.planner.TripPlanDraft.DraftItem;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.Set;

/** The planning rules of plan/03-ai-pipeline.md §4, one small class each. Messages are Vietnamese. */
public final class PlanRules {

    public static final int MAX_REASON_CHARS = 300;

    private PlanRules() {}

    public static List<PlanRule> all() {
        return List.of(new DayCount(), new ItemFields(), new UnknownPlace(), new Duplicates(), new Pace(),
                new SlotCapacity(), new ChildAge(), new RainOutdoor(), new Sources(), new Budget(), new Reasons(),
                new ClosedAtSlot(), new LockedDays(), new MealSlots());
    }

    /** 1. DAY_COUNT: exactly req.days days numbered 1..N. */
    static final class DayCount implements PlanRule {
        @Override
        public List<Violation> check(TripPlanDraft d, PlanContext ctx) {
            int expected = ctx.request().days();
            if (d.days().size() != expected) {
                return List.of(Violation.hard("DAY_COUNT",
                        "Lịch có %d ngày, cần đúng %d ngày.".formatted(d.days().size(), expected), null, null));
            }
            for (int i = 0; i < expected; i++) {
                if (d.days().get(i).dayIndex() != i + 1) {
                    return List.of(Violation.hard("DAY_COUNT",
                            "dayIndex phải lần lượt từ 1 đến %d.".formatted(expected), null, null));
                }
            }
            return List.of();
        }
    }

    /** Every item needs a place id and a slot. */
    static final class ItemFields implements PlanRule {
        @Override
        public List<Violation> check(TripPlanDraft d, PlanContext ctx) {
            List<Violation> out = new ArrayList<>();
            for (DraftDay day : d.days()) {
                for (DraftItem item : day.items()) {
                    if (item.placeId() == null || item.placeId().isBlank() || item.slot() == null) {
                        out.add(Violation.hard("MISSING_FIELD", "Mục thiếu placeId hoặc slot.", day.dayIndex(),
                                item.placeId()));
                    }
                }
            }
            return out;
        }
    }

    /** 2. UNKNOWN_PLACE: only candidate ids (locked days are checked by LockedDays). */
    static final class UnknownPlace implements PlanRule {
        @Override
        public List<Violation> check(TripPlanDraft d, PlanContext ctx) {
            Set<String> allowed = ctx.candidatesById().keySet();
            List<Violation> out = new ArrayList<>();
            for (DraftDay day : unlocked(d, ctx)) {
                for (DraftItem item : day.items()) {
                    if (item.placeId() != null && !allowed.contains(item.placeId())) {
                        out.add(Violation.hard("UNKNOWN_PLACE", "Địa điểm không có trong danh sách ứng viên.",
                                day.dayIndex(), item.placeId()));
                    }
                }
            }
            return out;
        }
    }

    /** 3. DUPLICATE_ACTIVITY (hard) and FOOD_REPEATED (soft). */
    static final class Duplicates implements PlanRule {
        @Override
        public List<Violation> check(TripPlanDraft d, PlanContext ctx) {
            Map<String, Place> places = ctx.knownPlaces();
            Set<String> seen = new HashSet<>();
            Set<String> reported = new HashSet<>();
            List<Violation> out = new ArrayList<>();
            for (DraftDay day : d.days()) {
                for (DraftItem item : day.items()) {
                    Place p = places.get(item.placeId());
                    if (p == null || seen.add(p.id()) || !reported.add(p.id())) {
                        continue;
                    }
                    out.add(p.type() == PlaceType.ACTIVITY
                            ? Violation.hard("DUPLICATE_ACTIVITY", "Hoạt động bị lặp lại: " + p.name() + ".",
                                    day.dayIndex(), p.id())
                            : Violation.soft("FOOD_REPEATED", "Quán " + p.name() + " được chọn nhiều lần.",
                                    day.dayIndex(), p.id()));
                }
            }
            return out;
        }
    }

    /** 4. PACE_EXCEEDED / EMPTY_DAY: 1..maxPerDay activities per day. */
    static final class Pace implements PlanRule {
        @Override
        public List<Violation> check(TripPlanDraft d, PlanContext ctx) {
            Map<String, Place> places = ctx.knownPlaces();
            int max = ctx.request().pace().maxActivitiesPerDay();
            List<Violation> out = new ArrayList<>();
            for (DraftDay day : unlocked(d, ctx)) {
                long n = day.items().stream().map(i -> places.get(i.placeId()))
                        .filter(p -> p != null && p.type() == PlaceType.ACTIVITY).count();
                if (n == 0) {
                    out.add(Violation.hard("EMPTY_DAY", "Ngày không có hoạt động nào.", day.dayIndex(), null));
                } else if (n > max) {
                    out.add(Violation.hard("PACE_EXCEEDED",
                            "Có %d hoạt động, nhịp độ chỉ cho phép tối đa %d.".formatted(n, max), day.dayIndex(), null));
                }
            }
            return out;
        }
    }

    /** 5. SLOT_OVERFLOW: slot capacities, long-visit placement and the end of the day. */
    static final class SlotCapacity implements PlanRule {
        @Override
        public List<Violation> check(TripPlanDraft d, PlanContext ctx) {
            Map<String, Place> places = ctx.knownPlaces();
            List<Violation> out = new ArrayList<>();
            for (DraftDay day : unlocked(d, ctx)) {
                Map<Slot, List<DraftItem>> bySlot = DaySchedule.bySlot(day, places);
                for (Slot slot : Slot.values()) {
                    List<Place> activities = bySlot.get(slot).stream().map(i -> places.get(i.placeId()))
                            .filter(p -> p.type() == PlaceType.ACTIVITY).toList();
                    Place longVisit = activities.stream().filter(DaySchedule::isLong).findFirst().orElse(null);
                    if (longVisit != null) {
                        checkLongVisit(slot, longVisit, activities.size(), bySlot, places, day.dayIndex(), out);
                        continue;
                    }
                    int minutes = activities.stream().mapToInt(Place::durationMin).sum();
                    if (minutes > DaySchedule.CAPACITY.get(slot)) {
                        out.add(Violation.hard("SLOT_OVERFLOW", "Buổi %s có %d phút hoạt động, tối đa %d."
                                .formatted(slot, minutes, DaySchedule.CAPACITY.get(slot)), day.dayIndex(), null));
                    }
                }
                List<DaySchedule.Timed> timeline = DaySchedule.schedule(day, places);
                if (!timeline.isEmpty() && timeline.getLast().endMin() > DaySchedule.DAY_END) {
                    out.add(Violation.hard("SLOT_OVERFLOW", "Lịch trong ngày kết thúc sau 22:30.", day.dayIndex(),
                            null));
                }
            }
            return out;
        }

        private static void checkLongVisit(Slot slot, Place visit, int activitiesInSlot,
                Map<Slot, List<DraftItem>> bySlot, Map<String, Place> places, int dayIndex, List<Violation> out) {
            if (slot == Slot.EVENING) {
                out.add(Violation.hard("SLOT_OVERFLOW", visit.name() + " kéo dài, không thể xếp buổi tối.",
                        dayIndex, visit.id()));
            } else if (activitiesInSlot > 1) {
                out.add(Violation.hard("SLOT_OVERFLOW", visit.name() + " kéo dài nên phải là hoạt động duy nhất của buổi.",
                        dayIndex, visit.id()));
            } else if (slot == Slot.MORNING && !bySlot.get(Slot.AFTERNOON).isEmpty()) {
                out.add(Violation.hard("SLOT_OVERFLOW", "Sau " + visit.name()
                        + " buổi chiều phải để trống (ăn trưa tại điểm đến).", dayIndex, visit.id()));
            } else if (slot == Slot.AFTERNOON && bySlot.get(Slot.EVENING).stream()
                    .anyMatch(i -> places.get(i.placeId()).type() == PlaceType.ACTIVITY)) {
                out.add(Violation.hard("SLOT_OVERFLOW", "Sau " + visit.name()
                        + " buổi tối chỉ nên ăn tối, không thêm hoạt động.", dayIndex, visit.id()));
            }
        }
    }

    /** 6. CHILD_AGE: no place below its minimum age. */
    static final class ChildAge implements PlanRule {
        @Override
        public List<Violation> check(TripPlanDraft d, PlanContext ctx) {
            OptionalInt youngest = ctx.request().youngestChildAge();
            if (youngest.isEmpty()) {
                return List.of();
            }
            Map<String, Place> places = ctx.knownPlaces();
            List<Violation> out = new ArrayList<>();
            forEachItem(d, (day, item) -> {
                Place p = places.get(item.placeId());
                if (p != null && p.childMinAge() > youngest.getAsInt()) {
                    out.add(Violation.hard("CHILD_AGE", p.name() + " yêu cầu trẻ từ " + p.childMinAge() + " tuổi.",
                            day.dayIndex(), p.id()));
                }
            });
            return out;
        }
    }

    /** 7. RAIN_OUTDOOR (soft): outdoor activity on a rainy day. */
    static final class RainOutdoor implements PlanRule {
        @Override
        public List<Violation> check(TripPlanDraft d, PlanContext ctx) {
            Map<String, Place> places = ctx.knownPlaces();
            List<Violation> out = new ArrayList<>();
            for (DraftDay day : d.days()) {
                if (!ctx.isRainy(day.dayIndex())) {
                    continue;
                }
                for (DraftItem item : day.items()) {
                    Place p = places.get(item.placeId());
                    if (p != null && p.type() == PlaceType.ACTIVITY && !p.indoor()) {
                        out.add(Violation.soft("RAIN_OUTDOOR", "Dự báo mưa nhưng " + p.name() + " ở ngoài trời.",
                                day.dayIndex(), p.id()));
                    }
                }
            }
            return out;
        }
    }

    /** 8. BAD_SOURCE (soft): cited evidence must exist; unknown ids are dropped by the assembler. */
    static final class Sources implements PlanRule {
        @Override
        public List<Violation> check(TripPlanDraft d, PlanContext ctx) {
            Set<String> known = ctx.evidenceIds();
            List<Violation> out = new ArrayList<>();
            forEachItem(d, (day, item) -> item.sourceIds().stream().filter(id -> !known.contains(id)).forEach(id ->
                    out.add(Violation.soft("BAD_SOURCE", "Nguồn " + id + " không tồn tại, đã bỏ.", day.dayIndex(),
                            item.placeId()))));
            return out;
        }
    }

    /** 9. OVER_BUDGET (hard, repairable): total group cost within the budget. */
    static final class Budget implements PlanRule {
        @Override
        public List<Violation> check(TripPlanDraft d, PlanContext ctx) {
            Map<String, Place> places = ctx.knownPlaces();
            List<Place> chosen = d.days().stream().flatMap(day -> day.items().stream())
                    .map(i -> places.get(i.placeId())).filter(Objects::nonNull).toList();
            long total = BudgetCalculator.totalOf(chosen, ctx.request());
            long limit = ctx.request().budgetVnd();
            if (total <= limit) {
                return List.of();
            }
            Place priciest = chosen.stream()
                    .max(Comparator.comparingLong(p -> BudgetCalculator.itemCost(p, ctx.request()))).orElseThrow();
            return List.of(Violation.hard("OVER_BUDGET", "Tổng chi phí %s vượt ngân sách %s; hãy thay mục đắt nhất: %s (%s)."
                    .formatted(Vnd.format(total), Vnd.format(limit), priciest.name(),
                            Vnd.format(BudgetCalculator.itemCost(priciest, ctx.request()))), null, priciest.id()));
        }
    }

    /** 10. BAD_REASON (soft): a non-empty reason of at most 300 characters. */
    static final class Reasons implements PlanRule {
        @Override
        public List<Violation> check(TripPlanDraft d, PlanContext ctx) {
            List<Violation> out = new ArrayList<>();
            forEachItem(d, (day, item) -> {
                if (item.reason() == null || item.reason().isBlank() || item.reason().length() > MAX_REASON_CHARS) {
                    out.add(Violation.soft("BAD_REASON", "Lý do trống hoặc quá dài.", day.dayIndex(), item.placeId()));
                }
            });
            return out;
        }
    }

    /** 11. CLOSED_AT_SLOT (soft): opening hours vs the scheduled time. */
    static final class ClosedAtSlot implements PlanRule {
        @Override
        public List<Violation> check(TripPlanDraft d, PlanContext ctx) {
            Map<String, Place> places = ctx.knownPlaces();
            List<Violation> out = new ArrayList<>();
            for (DraftDay day : d.days()) {
                for (DaySchedule.Timed t : DaySchedule.schedule(day, places)) {
                    OpeningHours hours = OpeningHours.parse(t.place().openingHours());
                    if (hours.isOpen(ctx.date(day.dayIndex()), t.start(), t.end()) == OpeningHours.Status.CLOSED) {
                        out.add(Violation.soft("CLOSED_AT_SLOT", "%s có thể đóng cửa lúc %s–%s (giờ mở: %s)."
                                .formatted(t.place().name(), t.start(), t.end(), t.place().openingHours()),
                                day.dayIndex(), t.place().id()));
                    }
                }
            }
            return out;
        }
    }

    /** 12. LOCKED_DAY_CHANGED: days the user did not ask to change stay identical. */
    static final class LockedDays implements PlanRule {
        @Override
        public List<Violation> check(TripPlanDraft d, PlanContext ctx) {
            List<Violation> out = new ArrayList<>();
            Map<Integer, DraftDay> byIndex = new HashMap<>();
            d.days().forEach(day -> byIndex.put(day.dayIndex(), day));
            ctx.lockedDays().forEach((index, locked) -> {
                DraftDay actual = byIndex.get(index);
                if (actual == null || !signature(actual).equals(signature(locked))) {
                    out.add(Violation.hard("LOCKED_DAY_CHANGED", "Ngày " + index + " phải giữ nguyên như trước.",
                            index, null));
                }
            });
            return out;
        }

        private static List<String> signature(DraftDay day) {
            return day.items().stream().map(i -> i.slot() + ":" + i.placeId()).toList();
        }
    }

    /** MEAL_SLOT (soft): meals at lunch (AFTERNOON) or dinner (EVENING), one per slot. */
    static final class MealSlots implements PlanRule {
        @Override
        public List<Violation> check(TripPlanDraft d, PlanContext ctx) {
            Map<String, Place> places = ctx.knownPlaces();
            List<Violation> out = new ArrayList<>();
            for (DraftDay day : unlocked(d, ctx)) {
                Map<Slot, List<DraftItem>> bySlot = DaySchedule.bySlot(day, places);
                for (Slot slot : Slot.values()) {
                    long meals = bySlot.get(slot).stream()
                            .filter(i -> places.get(i.placeId()).type() == PlaceType.FOOD).count();
                    if (meals > 0 && slot == Slot.MORNING) {
                        out.add(Violation.soft("MEAL_SLOT", "Bữa ăn nên xếp trưa hoặc tối.", day.dayIndex(), null));
                    } else if (meals > 1) {
                        out.add(Violation.soft("MEAL_SLOT", "Có hơn một bữa ăn trong buổi " + slot + ".",
                                day.dayIndex(), null));
                    }
                }
            }
            return out;
        }
    }

    // ---- helpers ---------------------------------------------------------------------------------------------

    private static List<DraftDay> unlocked(TripPlanDraft d, PlanContext ctx) {
        return d.days().stream().filter(day -> !ctx.isLocked(day.dayIndex())).toList();
    }

    private interface ItemVisitor {
        void visit(DraftDay day, DraftItem item);
    }

    private static void forEachItem(TripPlanDraft d, ItemVisitor visitor) {
        for (DraftDay day : d.days()) {
            for (DraftItem item : day.items()) {
                visitor.visit(day, item);
            }
        }
    }
}
