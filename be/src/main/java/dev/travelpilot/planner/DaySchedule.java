package dev.travelpilot.planner;

import dev.travelpilot.catalog.Place;
import dev.travelpilot.common.domain.PlaceType;
import dev.travelpilot.common.domain.Slot;
import dev.travelpilot.planner.TripPlanDraft.DraftDay;
import dev.travelpilot.planner.TripPlanDraft.DraftItem;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The single definition of a day's timeline, shared by the validator, the greedy fallback and the assembler
 * (plan/03-ai-pipeline.md §6). Items run one after another with 30 minutes of travel in between, so they can
 * never overlap; meals go first in AFTERNOON (lunch 12:00) and EVENING (dinner 18:30).
 */
public final class DaySchedule {

    public static final int TRAVEL_MIN = 30;
    public static final int LONG_VISIT_MIN = 210;
    static final int MORNING_START = 8 * 60 + 30;
    static final int LONG_MORNING_START = 8 * 60;
    static final int LUNCH_START = 12 * 60;
    static final int AFTERNOON_START = 13 * 60 + 30;
    static final int EVENING_START = 18 * 60 + 30;
    public static final int DAY_END = 22 * 60 + 30;
    private static final int LAST_MINUTE = 23 * 60 + 59;

    /** Maximum total ACTIVITY minutes per slot (long visits are handled separately). */
    public static final Map<Slot, Integer> CAPACITY = Map.of(Slot.MORNING, 210, Slot.AFTERNOON, 240, Slot.EVENING, 180);

    /** One scheduled item; minutes since midnight (may exceed 24:00 when a plan overflows). */
    public record Timed(DraftItem item, Place place, int startMin, int endMin) {

        public LocalTime start() {
            return toTime(startMin);
        }

        public LocalTime end() {
            return toTime(endMin);
        }
    }

    private DaySchedule() {}

    /** A long visit takes a whole half-day: day trips and anything over 210 minutes. */
    public static boolean isLong(Place place) {
        return place.type() == PlaceType.ACTIVITY && (place.dayTrip() || place.durationMin() > LONG_VISIT_MIN);
    }

    /** Minutes a slot's activities need: their durations plus travel between them. */
    public static int activityMinutes(List<Place> activities) {
        int sum = activities.stream().mapToInt(Place::durationMin).sum();
        return activities.isEmpty() ? 0 : sum + TRAVEL_MIN * (activities.size() - 1);
    }

    /** Items grouped by slot in timeline order; items without slot or with unknown places are skipped. */
    public static Map<Slot, List<DraftItem>> bySlot(DraftDay day, Map<String, Place> places) {
        Map<Slot, List<DraftItem>> map = new EnumMap<>(Slot.class);
        for (Slot s : Slot.values()) {
            map.put(s, new ArrayList<>());
        }
        for (DraftItem item : day.items()) {
            if (item.slot() != null && places.containsKey(item.placeId())) {
                map.get(item.slot()).add(item);
            }
        }
        // Meals first in the afternoon (lunch) and evening (dinner); stable sort keeps the planner's order.
        Comparator<DraftItem> mealsFirst =
                Comparator.comparingInt(i -> places.get(i.placeId()).type() == PlaceType.FOOD ? 0 : 1);
        map.get(Slot.AFTERNOON).sort(mealsFirst);
        map.get(Slot.EVENING).sort(mealsFirst);
        return map;
    }

    public static List<Timed> schedule(DraftDay day, Map<String, Place> places) {
        List<Timed> timeline = new ArrayList<>();
        Integer cursor = null;
        for (Map.Entry<Slot, List<DraftItem>> entry : bySlot(day, places).entrySet()) {
            for (DraftItem item : entry.getValue()) {
                Place place = places.get(item.placeId());
                int earliest = earliest(entry.getKey(), place, timeline.isEmpty());
                int start = cursor == null ? earliest : Math.max(earliest, cursor + TRAVEL_MIN);
                int end = start + place.durationMin();
                timeline.add(new Timed(item, place, start, end));
                cursor = end;
            }
        }
        return timeline;
    }

    private static int earliest(Slot slot, Place place, boolean firstOfDay) {
        return switch (slot) {
            case MORNING -> firstOfDay && isLong(place) ? LONG_MORNING_START : MORNING_START;
            case AFTERNOON -> place.type() == PlaceType.FOOD ? LUNCH_START : AFTERNOON_START;
            case EVENING -> EVENING_START;
        };
    }

    static LocalTime toTime(int minutes) {
        int m = Math.min(minutes, LAST_MINUTE);
        return LocalTime.of(m / 60, m % 60);
    }
}
