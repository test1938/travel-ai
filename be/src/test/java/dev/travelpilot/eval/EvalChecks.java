package dev.travelpilot.eval;

import dev.travelpilot.catalog.Place;
import dev.travelpilot.common.domain.PlaceType;
import dev.travelpilot.conversation.ChatService;
import dev.travelpilot.security.PromptGuard;
import dev.travelpilot.trip.TripPlan;
import dev.travelpilot.trip.TripPlan.DayPlan;
import dev.travelpilot.trip.TripPlan.PlanItem;
import dev.travelpilot.trip.TripRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/** The fixed eval checks (T8.02): deterministic, computed from the plan and the catalog, never by a model. */
public final class EvalChecks {

    static final long DEFAULT_MAX_LATENCY_MS = 45_000;
    static final int DEFAULT_MAX_TOKENS = 20_000;

    private EvalChecks() {}

    /** Hard rules of a finished plan plus the case's place expectations. */
    public static List<Check> plan(EvalCase c, TripRequest req, TripPlan plan, Map<String, Place> catalog) {
        List<Check> checks = new ArrayList<>();
        EvalCase.Expect e = c.expect();
        List<PlanItem> items = plan.days().stream().flatMap(d -> d.items().stream()).toList();
        Set<String> ids = items.stream().map(PlanItem::placeId).collect(Collectors.toSet());

        checks.add(schema(req, plan));
        List<String> unknown = items.stream().map(PlanItem::placeId)
                .filter(id -> !catalog.containsKey(id) || catalog.get(id).destination() != req.destination())
                .distinct().toList();
        checks.add(Check.hard("ids", unknown.isEmpty(), "unknown or other-destination ids " + unknown));

        if (!req.childrenAges().isEmpty() || Boolean.TRUE.equals(e.childSafe())) {
            int youngest = req.childrenAges().stream().mapToInt(Integer::intValue).min().orElse(0);
            List<String> unsafe = items.stream().map(i -> catalog.get(i.placeId())).filter(Objects::nonNull)
                    .filter(p -> p.childMinAge() > youngest)
                    .map(p -> p.id() + " (" + p.childMinAge() + "+)").distinct().toList();
            checks.add(Check.hard("childAge", unsafe.isEmpty(), "youngest " + youngest + ": " + unsafe));
        }

        int maxActivities = e.maxActivitiesPerDay() != null ? e.maxActivitiesPerDay()
                : req.pace().maxActivitiesPerDay();
        List<String> busy = plan.days().stream().filter(d -> activities(d) > maxActivities)
                .map(d -> "day " + d.dayIndex() + ": " + activities(d)).toList();
        checks.add(Check.hard("pace", busy.isEmpty(), "max " + maxActivities + " activities, " + busy));

        if (Boolean.TRUE.equals(e.withinBudget())) {
            long limit = e.maxBudgetVnd() != null ? e.maxBudgetVnd() : req.budgetVnd();
            long total = plan.budget().totalVnd();
            checks.add(Check.hard("budget", total <= limit, "total " + total + " > limit " + limit));
        }

        if (!e.mustIncludeAnyOf().isEmpty()) {
            boolean hit = e.mustIncludeAnyOf().stream().anyMatch(ids::contains);
            checks.add(Check.expect("mustIncludeAnyOf", hit, "none of " + e.mustIncludeAnyOf()));
        }
        if (!e.mustNotInclude().isEmpty()) {
            List<String> present = e.mustNotInclude().stream().filter(ids::contains).toList();
            checks.add(Check.expect("mustNotInclude", present.isEmpty(), "contains " + present));
        }
        return checks;
    }

    /** Checks of a chat turn: routing, versioning, refusal, sources, and day locks against version 1. */
    public static List<Check> chat(EvalCase c, ChatService.ChatReply reply, TripPlan before, TripPlan after) {
        List<Check> checks = new ArrayList<>();
        EvalCase.Expect e = c.expect();
        boolean changed = after.version() != before.version();
        if (e.intent() != null) {
            checks.add(Check.expect("intent", reply.intent() == e.intent(), "got " + reply.intent()));
        }
        if (e.newVersion() != null) {
            checks.add(Check.expect("newVersion", changed == e.newVersion(),
                    "version " + before.version() + " -> " + after.version()));
        }
        if (Boolean.TRUE.equals(e.planUnchanged())) {
            checks.add(Check.expect("planUnchanged", !changed, "plan changed to version " + after.version()));
        }
        if (Boolean.TRUE.equals(e.refusal())) {
            boolean refused = PromptGuard.REFUSAL.equals(reply.reply())
                    || reply.intent() == dev.travelpilot.conversation.Intent.OUT_OF_SCOPE;
            checks.add(Check.expect("refusal", refused, "answered: " + shorten(reply.reply())));
        }
        if (Boolean.TRUE.equals(e.hasSources())) {
            checks.add(Check.expect("hasSources", !reply.sources().isEmpty(), "no sources"));
        }
        if (!e.mustMentionAnyOf().isEmpty()) {
            String answer = reply.reply().toLowerCase(Locale.ROOT);
            boolean hit = e.mustMentionAnyOf().stream().anyMatch(m -> answer.contains(m.toLowerCase(Locale.ROOT)));
            checks.add(Check.expect("mustMentionAnyOf", hit, "none of " + e.mustMentionAnyOf()));
        }
        for (int day : e.lockedDays()) {
            boolean same = placeIds(before, day).equals(placeIds(after, day));
            checks.add(Check.hard("lockedDay" + day, same, placeIds(before, day) + " -> " + placeIds(after, day)));
        }
        if (e.indoorDay() != null) {
            List<String> outdoor = after.days().stream().filter(d -> d.dayIndex() == e.indoorDay())
                    .flatMap(d -> d.items().stream())
                    .filter(i -> i.type() == PlaceType.ACTIVITY && !i.indoor())
                    .map(PlanItem::placeId).toList();
            checks.add(Check.expect("indoorDay" + e.indoorDay(), outdoor.isEmpty(), "outdoor " + outdoor));
        }
        return checks;
    }

    /** Latency and token budgets (defaults 45 s, 20k tokens per case). */
    public static List<Check> cost(EvalCase c, long latencyMs, long tokens) {
        long maxLatency = c.expect().maxLatencyMs() != null ? c.expect().maxLatencyMs() : DEFAULT_MAX_LATENCY_MS;
        long maxTokens = c.expect().maxTokens() != null ? c.expect().maxTokens() : DEFAULT_MAX_TOKENS;
        return List.of(
                Check.expect("latency", latencyMs <= maxLatency, latencyMs + " ms > " + maxLatency),
                Check.expect("tokens", tokens <= maxTokens, tokens + " > " + maxTokens));
    }

    private static Check schema(TripRequest req, TripPlan plan) {
        List<String> problems = new ArrayList<>();
        if (plan.days().size() != req.days()) {
            problems.add(plan.days().size() + " days instead of " + req.days());
        }
        for (int i = 0; i < plan.days().size(); i++) {
            DayPlan d = plan.days().get(i);
            if (d.dayIndex() != i + 1) {
                problems.add("day index " + d.dayIndex() + " at position " + (i + 1));
            }
            if (d.items().isEmpty()) {
                problems.add("day " + d.dayIndex() + " is empty");
            }
            if (!Objects.equals(d.date(), req.dateOf(d.dayIndex()))) {
                problems.add("day " + d.dayIndex() + " date " + d.date());
            }
        }
        if (plan.budget() == null) {
            problems.add("no budget");
        }
        return Check.hard("schema", problems.isEmpty(), String.join("; ", problems));
    }

    private static long activities(DayPlan d) {
        return d.items().stream().filter(i -> i.type() == PlaceType.ACTIVITY).count();
    }

    private static List<String> placeIds(TripPlan plan, int day) {
        return plan.days().stream().filter(d -> d.dayIndex() == day).flatMap(d -> d.items().stream())
                .map(PlanItem::placeId).toList();
    }

    private static String shorten(String s) {
        return s == null || s.length() <= 80 ? s : s.substring(0, 80) + "…";
    }
}
