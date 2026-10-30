package dev.travelpilot.planner;

import dev.travelpilot.catalog.Place;
import dev.travelpilot.common.Vnd;
import dev.travelpilot.common.domain.PlaceTag;
import dev.travelpilot.integration.weather.DayWeather;
import dev.travelpilot.planner.TripPlanDraft.DraftDay;
import dev.travelpilot.recommendation.ScoredPlace;
import dev.travelpilot.trip.TripRequest;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/** Turns the planning data into compact prompt sections. Pure functions, unit-tested. */
public final class PlannerPromptFormatter {

    static final String CANDIDATE_HEADER =
            "id | name | type | tags | costVnd | durMin | bestTime | indoor | dayTrip | score";

    private PlannerPromptFormatter() {}

    /** One row per candidate; costVnd is the cost for the whole group. Pipes in names are removed. */
    public static String candidates(List<ScoredPlace> places, TripRequest req) {
        StringBuilder sb = new StringBuilder(CANDIDATE_HEADER);
        for (ScoredPlace s : places) {
            Place p = s.place();
            sb.append('\n').append(String.join(" | ",
                    p.id(),
                    clean(p.name()),
                    p.type().name(),
                    p.tags().stream().map(PlaceTag::code).collect(Collectors.joining(",")),
                    Long.toString(req.groupCost(p)),
                    Integer.toString(p.durationMin()),
                    p.bestTime().name(),
                    Boolean.toString(p.indoor()),
                    Boolean.toString(p.dayTrip()),
                    String.format(Locale.ROOT, "%.2f", s.score().total())));
        }
        return sb.toString();
    }

    /** "[ev-n | placeId] title: snippet" lines, at most 400 characters of text each. */
    public static String evidence(List<PlanEvidence> evidence) {
        if (evidence.isEmpty()) {
            return "(none)";
        }
        return evidence.stream()
                .map(e -> "[%s | %s] %s: %s".formatted(e.evId(),
                        e.evidence().placeId() == null ? "-" : e.evidence().placeId(),
                        clean(e.evidence().title()), clean(e.evidence().snippet())))
                .collect(Collectors.joining("\n"));
    }

    public static String request(TripRequest req) {
        String kids = req.hasChildren()
                ? ", %d trẻ em (tuổi: %s)".formatted(req.children(),
                        req.childrenAges().stream().map(String::valueOf).collect(Collectors.joining(", ")))
                : ", không có trẻ em";
        String prefs = req.preferences().isEmpty() ? "không nêu"
                : req.preferences().stream().map(PlaceTag::code).sorted().collect(Collectors.joining(", "));
        StringBuilder sb = new StringBuilder()
                .append("Điểm đến: ").append(req.destination().displayName())
                .append(". Ngày đi: ").append(req.startDate()).append(", ").append(req.days()).append(" ngày.")
                .append("\nNhóm: ").append(req.adults()).append(" người lớn").append(kids).append('.')
                .append("\nNgân sách vé tham quan + ăn uống cho cả nhóm: ").append(req.budgetVnd())
                .append(" VND (").append(Vnd.format(req.budgetVnd())).append(").")
                .append("\nSở thích: ").append(prefs).append('.')
                .append("\nNhịp độ: ").append(req.pace().name())
                .append(" (tối đa ").append(req.pace().maxActivitiesPerDay()).append(" hoạt động/ngày).");
        if (!req.notes().isEmpty()) {
            sb.append("\n<notes>\n").append(req.notes().stream().map(PlannerPromptFormatter::clean)
                    .collect(Collectors.joining("\n"))).append("\n</notes>");
        }
        return sb.toString();
    }

    public static String weather(TripRequest req, Map<LocalDate, DayWeather> forecast) {
        StringBuilder sb = new StringBuilder();
        for (int day = 1; day <= req.days(); day++) {
            LocalDate date = req.dateOf(day);
            DayWeather w = forecast.get(date);
            String text = w == null ? "chưa có dự báo" : w.rainy() ? "rain (mưa)" : "dry (không mưa)";
            sb.append("Day ").append(day).append(" (").append(date).append("): ").append(text);
            if (day < req.days()) {
                sb.append('\n');
            }
        }
        return sb.toString();
    }

    public static String childRule(TripRequest req) {
        return req.youngestChildAge().isPresent()
                ? "The youngest child is " + req.youngestChildAge().getAsInt()
                        + " years old; all candidates already allow this age."
                : "No children on this trip.";
    }

    /** Locked days as "Day n: slot placeId, ..." or "(none)". */
    public static String locked(Map<Integer, DraftDay> lockedDays) {
        if (lockedDays.isEmpty()) {
            return "(none)";
        }
        return lockedDays.values().stream()
                .sorted((a, b) -> Integer.compare(a.dayIndex(), b.dayIndex()))
                .map(d -> "Day " + d.dayIndex() + " (keep exactly): " + d.items().stream()
                        .map(i -> i.slot() + " " + i.placeId()).collect(Collectors.joining(", ")))
                .collect(Collectors.joining("\n"));
    }

    /** Problems for the repair prompt, one per line. */
    public static String problems(List<Violation> violations) {
        return violations.stream()
                .map(v -> "- " + v.code() + ": " + v.message()
                        + (v.dayIndex() == null ? "" : " (day " + v.dayIndex() + ")")
                        + (v.placeId() == null ? "" : " [" + v.placeId() + "]"))
                .collect(Collectors.joining("\n"));
    }

    private static String clean(String text) {
        return text == null ? "" : text.replace('|', '/').replace('\n', ' ').strip();
    }
}
