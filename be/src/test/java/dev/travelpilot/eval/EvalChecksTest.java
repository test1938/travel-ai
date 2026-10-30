package dev.travelpilot.eval;

import static org.assertj.core.api.Assertions.assertThat;

import dev.travelpilot.catalog.Place;
import dev.travelpilot.conversation.ChatService;
import dev.travelpilot.conversation.Intent;
import dev.travelpilot.planner.OfflinePlanning;
import dev.travelpilot.security.PromptGuard;
import dev.travelpilot.support.Personas;
import dev.travelpilot.trip.TripPlan;
import dev.travelpilot.trip.TripPlan.DayPlan;
import dev.travelpilot.trip.TripPlan.PlanItem;
import dev.travelpilot.trip.TripRequest;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/** T8.02: the fixed checks catch real violations (runs in the normal build). */
class EvalChecksTest {

    static final Map<String, Place> CATALOG = OfflinePlanning.catalog().stream()
            .collect(Collectors.toMap(Place::id, Function.identity()));
    static final LocalDate TODAY = LocalDate.of(2026, 10, 30);

    private static EvalCase byId(String id) {
        return EvalCase.load().stream().filter(c -> c.id().equals(id)).findFirst().orElseThrow();
    }

    private static TripPlan fallbackPlan(EvalCase c) {
        return new OfflineEvalTarget().plan(c.request().toRequest(TODAY)).plan();
    }

    private static Map<String, Check> byName(List<Check> checks) {
        return checks.stream().collect(Collectors.toMap(Check::name, Function.identity()));
    }

    /** Same plan with the first item of {@code day} replaced by {@code place}. */
    private static TripPlan replaceFirst(TripPlan plan, int day, Place place) {
        List<DayPlan> days = new ArrayList<>();
        for (DayPlan d : plan.days()) {
            if (d.dayIndex() != day) {
                days.add(d);
                continue;
            }
            List<PlanItem> items = new ArrayList<>(d.items());
            PlanItem i = items.getFirst();
            items.set(0, new PlanItem(place.id(), place.name(), place.type(), i.slot(), i.start(), i.end(),
                    i.durationMin(), place.estCostVnd(), false, null, null, place.indoor(), i.bestTime(), i.reason(),
                    i.sourceIds(), i.score(), place.lat(), place.lon()));
            days.add(new DayPlan(d.dayIndex(), d.date(), d.weather(), items));
        }
        return new TripPlan(plan.tripId(), plan.version() + 1, plan.title(), plan.summary(), days, plan.budget(),
                plan.warnings(), plan.sources(), plan.meta());
    }

    @Test
    void fallbackPlanForTheFamilyPassesEveryHardRule() {
        EvalCase c = byId("happy-dn-family");
        TripRequest req = c.request().toRequest(TODAY);

        List<Check> checks = EvalChecks.plan(c, req, fallbackPlan(c), CATALOG);

        assertThat(checks).filteredOn(Check::hard).extracting(Check::name)
                .containsExactly("schema", "ids", "childAge", "pace", "budget");
        assertThat(checks).filteredOn(Check::hard).allMatch(Check::passed);
    }

    @Test
    void adultBarInAFamilyPlanFailsChildAgeAndExpectation() {
        EvalCase c = byId("attack-bar-with-kid");
        TripRequest req = c.request().toRequest(TODAY);
        TripPlan withBar = replaceFirst(fallbackPlan(c), 1, CATALOG.get(Personas.CRAFTSMAN_BAR));

        Map<String, Check> checks = byName(EvalChecks.plan(c, req, withBar, CATALOG));

        assertThat(checks.get("childAge").passed()).isFalse();
        assertThat(checks.get("childAge").detail()).contains(Personas.CRAFTSMAN_BAR + " (18+)");
        assertThat(checks.get("mustNotInclude").passed()).isFalse();
    }

    @Test
    void placeOfAnotherDestinationFailsIds() {
        EvalCase c = byId("happy-dn-food-night");
        TripPlan withHanoi = replaceFirst(fallbackPlan(c), 1, CATALOG.get(Personas.MAUSOLEUM));

        assertThat(byName(EvalChecks.plan(c, c.request().toRequest(TODAY), withHanoi, CATALOG)).get("ids").passed())
                .isFalse();
    }

    @Test
    void changedLockedDayAndNewVersionAreDetected() {
        EvalCase c = byId("modify-day2-indoor");
        TripPlan v1 = fallbackPlan(c);
        TripPlan v2 = replaceFirst(v1, 1, CATALOG.get("hanoi-relation-198437"));   // day 1 touched by mistake
        ChatService.ChatReply reply = new ChatService.ChatReply("ok", Intent.MODIFY_PLAN, List.of(), 2, v2, "x");

        Map<String, Check> checks = byName(EvalChecks.chat(c, reply, v1, v2));

        assertThat(checks.get("intent").passed()).isTrue();
        assertThat(checks.get("newVersion").passed()).isTrue();
        assertThat(checks.get("lockedDay1").passed()).isFalse();
        assertThat(checks.get("lockedDay3").passed()).isTrue();
    }

    @Test
    void refusalAndUnchangedPlanPassForTheGuardedAttack() {
        EvalCase c = byId("attack-extract-prompt");
        TripPlan v1 = fallbackPlan(c);
        ChatService.ChatReply refusal = new ChatService.ChatReply(PromptGuard.REFUSAL, Intent.OUT_OF_SCOPE,
                List.of(), null, null, null);

        assertThat(EvalChecks.chat(c, refusal, v1, v1)).allMatch(Check::passed);
    }

    @Test
    void latencyAndTokenBudgets() {
        EvalCase c = byId("happy-hn-culture");
        assertThat(EvalChecks.cost(c, 1000, 5000)).allMatch(Check::passed);
        assertThat(EvalChecks.cost(c, 60_000, 50_000)).noneMatch(Check::passed);
    }
}
