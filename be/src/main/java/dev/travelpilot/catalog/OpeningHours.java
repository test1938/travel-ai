package dev.travelpilot.catalog;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.Month;
import java.time.MonthDay;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Minimal parser for the OSM {@code opening_hours} strings used in the catalog (plan/01-dataset.md §6).
 *
 * <p>Semantics: rules separated by ";" are applied in order and a later matching rule replaces the hours of that
 * day (so "... ; 2026 Sep 04-2026 Nov 02 off" closes those dates). Rules separated by ", " add hours. A time range
 * ending before it starts runs past midnight. "PH" (public holiday) is ignored because no holiday calendar is
 * available; "sunrise-sunset" is approximated as 06:00-18:00. A string that cannot be parsed yields
 * {@link Status#UNKNOWN} everywhere instead of an exception.
 */
public final class OpeningHours {

    /** Result of a check; UNKNOWN means the string could not be parsed and the caller should only warn. */
    public enum Status { OPEN, CLOSED, UNKNOWN }

    private static final int DAY = 24 * 60;
    private static final String DAY_TOKEN = "(?:Mo|Tu|We|Th|Fr|Sa|Su|PH)";
    private static final String MONTH_TOKEN = "(?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)";
    private static final Pattern ABSOLUTE_RANGE = Pattern.compile(
            "^(\\d{4}) (" + MONTH_TOKEN + ") (\\d{1,2})-(\\d{4}) (" + MONTH_TOKEN + ") (\\d{1,2})(?:\\s+|$)");
    private static final Pattern MONTH_DAY_RANGE = Pattern.compile(
            "^(" + MONTH_TOKEN + ") (\\d{1,2})-(" + MONTH_TOKEN + ") (\\d{1,2})(?:\\s+|$)");
    private static final Pattern MONTH_RANGE =
            Pattern.compile("^(" + MONTH_TOKEN + ")(?:-(" + MONTH_TOKEN + "))?(?:\\s+|$)");
    private static final Pattern WEEKDAYS = Pattern.compile(
            "^(" + DAY_TOKEN + "(?:-" + DAY_TOKEN + ")?(?:," + DAY_TOKEN + "(?:-" + DAY_TOKEN + ")?)*)(?:\\s+|$)");
    private static final Pattern TIME_LIST =
            Pattern.compile("^\\d{1,2}:\\d{2}-\\d{1,2}:\\d{2}(?:\\s*,\\s*\\d{1,2}:\\d{2}-\\d{1,2}:\\d{2})*$");
    private static final Pattern ADDITIVE_SEPARATOR = Pattern.compile(",\\s+(?=[A-Za-z])");

    private static final OpeningHours UNKNOWN = new OpeningHours(null, List.of());

    private final String source;
    /** Rule groups separated by ";"; rules inside a group were separated by ", " and add up. */
    private final List<List<Rule>> groups;

    private OpeningHours(String source, List<List<Rule>> groups) {
        this.source = source;
        this.groups = groups;
    }

    /** Never throws; returns an instance whose checks answer UNKNOWN when the string is not understood. */
    public static OpeningHours parse(String value) {
        if (value == null || value.isBlank()) {
            return UNKNOWN;
        }
        try {
            List<List<Rule>> groups = new ArrayList<>();
            for (String group : value.split(";")) {
                if (group.isBlank()) {
                    continue;
                }
                List<Rule> rules = new ArrayList<>();
                for (String rule : ADDITIVE_SEPARATOR.split(group.trim())) {
                    rules.add(parseRule(rule.trim()));
                }
                groups.add(List.copyOf(rules));
            }
            return groups.isEmpty() ? UNKNOWN : new OpeningHours(value, List.copyOf(groups));
        } catch (IllegalArgumentException e) {
            return UNKNOWN;
        }
    }

    public boolean isKnown() {
        return source != null;
    }

    /** OPEN if the place opens at some time on that date. */
    public Status isOpen(LocalDate date) {
        if (!isKnown()) {
            return Status.UNKNOWN;
        }
        return rangesOn(date).isEmpty() ? Status.CLOSED : Status.OPEN;
    }

    public Status isOpenAt(LocalDate date, LocalTime time) {
        return isOpen(date, time, time);
    }

    /** OPEN only if the whole interval [from, to] on that date falls inside one opening range. */
    public Status isOpen(LocalDate date, LocalTime from, LocalTime to) {
        if (!isKnown()) {
            return Status.UNKNOWN;
        }
        int start = minutes(from);
        int end = minutes(to) < start ? minutes(to) + DAY : minutes(to);
        for (TimeRange r : rangesOn(date)) {
            if (r.contains(start, end)) {
                return Status.OPEN;
            }
        }
        // A range of the previous day that runs past midnight (e.g. 18:00-02:00) also covers early hours.
        for (TimeRange r : rangesOn(date.minusDays(1))) {
            if (r.end() > DAY && r.contains(start + DAY, end + DAY)) {
                return Status.OPEN;
            }
        }
        return Status.CLOSED;
    }

    @Override
    public String toString() {
        return isKnown() ? source : "UNKNOWN";
    }

    private List<TimeRange> rangesOn(LocalDate date) {
        List<TimeRange> ranges = List.of();
        for (List<Rule> group : groups) {
            List<TimeRange> matched = null;
            for (Rule rule : group) {
                if (rule.matches(date)) {
                    if (matched == null) {
                        matched = new ArrayList<>();
                    }
                    matched.addAll(rule.times());
                }
            }
            if (matched != null) {
                ranges = matched;   // a later ";" rule replaces the hours of the day
            }
        }
        return ranges;
    }

    // ---- parsing -------------------------------------------------------------------------------------------

    private static Rule parseRule(String text) {
        String rest = text;
        DateSelector dates = null;
        Matcher m;
        if ((m = ABSOLUTE_RANGE.matcher(rest)).find()) {
            LocalDate from = LocalDate.of(Integer.parseInt(m.group(1)), month(m.group(2)), Integer.parseInt(m.group(3)));
            LocalDate to = LocalDate.of(Integer.parseInt(m.group(4)), month(m.group(5)), Integer.parseInt(m.group(6)));
            dates = d -> !d.isBefore(from) && !d.isAfter(to);
            rest = rest.substring(m.end());
        } else if ((m = MONTH_DAY_RANGE.matcher(rest)).find()) {
            MonthDay from = MonthDay.of(month(m.group(1)), Integer.parseInt(m.group(2)));
            MonthDay to = MonthDay.of(month(m.group(3)), Integer.parseInt(m.group(4)));
            dates = d -> inWrappingRange(MonthDay.from(d), from, to);
            rest = rest.substring(m.end());
        } else if ((m = MONTH_RANGE.matcher(rest)).find()) {
            Month from = month(m.group(1));
            Month to = m.group(2) == null ? from : month(m.group(2));
            dates = d -> inWrappingRange(d.getMonth(), from, to);
            rest = rest.substring(m.end());
        }

        Set<DayOfWeek> days = null;
        if ((m = WEEKDAYS.matcher(rest)).find()) {
            days = weekdays(m.group(1));
            rest = rest.substring(m.end());
        }

        rest = rest.trim();
        List<TimeRange> times;
        if (rest.equals("off") || rest.equals("closed")) {
            times = List.of();
        } else if (rest.equals("24/7") || rest.isEmpty()) {
            times = List.of(new TimeRange(0, DAY));
        } else if (rest.equals("sunrise-sunset")) {
            times = List.of(new TimeRange(6 * 60, 18 * 60));
        } else if (TIME_LIST.matcher(rest).matches()) {
            times = new ArrayList<>();
            for (String range : rest.split("\\s*,\\s*")) {
                times.add(timeRange(range));
            }
        } else {
            throw new IllegalArgumentException("Unsupported opening hours part: " + rest);
        }
        if (dates == null && days == null && times.isEmpty()) {
            throw new IllegalArgumentException("Rule without selector: " + text);
        }
        return new Rule(dates, days, List.copyOf(times));
    }

    private static Set<DayOfWeek> weekdays(String spec) {
        Set<DayOfWeek> days = EnumSet.noneOf(DayOfWeek.class);
        for (String item : spec.split(",")) {
            if (item.equals("PH")) {
                continue;   // no holiday calendar; rules list PH next to Sa,Su anyway
            }
            String[] bounds = item.split("-");
            DayOfWeek from = day(bounds[0]);
            DayOfWeek to = bounds.length > 1 ? day(bounds[1]) : from;
            for (DayOfWeek d = from; ; d = d.plus(1)) {
                days.add(d);
                if (d == to) {
                    break;
                }
            }
        }
        return days;
    }

    private static TimeRange timeRange(String text) {
        String[] parts = text.split("-");
        int start = parseTime(parts[0]);
        int end = parseTime(parts[1]);
        if (end <= start) {
            end += DAY;   // runs past midnight
        }
        return new TimeRange(start, end);
    }

    private static int parseTime(String hhmm) {
        String[] p = hhmm.trim().split(":");
        int h = Integer.parseInt(p[0]);
        int min = Integer.parseInt(p[1]);
        if (h > 24 || min > 59 || (h == 24 && min > 0)) {
            throw new IllegalArgumentException("Bad time " + hhmm);
        }
        return h * 60 + min;
    }

    private static DayOfWeek day(String token) {
        return switch (token) {
            case "Mo" -> DayOfWeek.MONDAY;
            case "Tu" -> DayOfWeek.TUESDAY;
            case "We" -> DayOfWeek.WEDNESDAY;
            case "Th" -> DayOfWeek.THURSDAY;
            case "Fr" -> DayOfWeek.FRIDAY;
            case "Sa" -> DayOfWeek.SATURDAY;
            case "Su" -> DayOfWeek.SUNDAY;
            default -> throw new IllegalArgumentException("Bad weekday " + token);
        };
    }

    private static Month month(String token) {
        for (Month m : Month.values()) {
            if (m.name().substring(0, 3).equalsIgnoreCase(token)) {
                return m;
            }
        }
        throw new IllegalArgumentException("Bad month " + token);
    }

    private static <T extends Comparable<T>> boolean inWrappingRange(T value, T from, T to) {
        return from.compareTo(to) <= 0
                ? value.compareTo(from) >= 0 && value.compareTo(to) <= 0
                : value.compareTo(from) >= 0 || value.compareTo(to) <= 0;   // e.g. Oct-Feb, Nov 01-Apr 15
    }

    private static int minutes(LocalTime t) {
        return t.getHour() * 60 + t.getMinute();
    }

    @FunctionalInterface
    private interface DateSelector {
        boolean matches(LocalDate date);
    }

    private record TimeRange(int start, int end) {
        boolean contains(int from, int to) {
            return from >= start && to <= end;
        }
    }

    private record Rule(DateSelector dates, Set<DayOfWeek> days, List<TimeRange> times) {
        boolean matches(LocalDate date) {
            return (dates == null || dates.matches(date)) && (days == null || days.contains(date.getDayOfWeek()));
        }
    }
}
