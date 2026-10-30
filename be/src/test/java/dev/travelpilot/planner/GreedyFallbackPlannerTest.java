package dev.travelpilot.planner;

import static org.assertj.core.api.Assertions.assertThat;

import dev.travelpilot.catalog.Place;
import dev.travelpilot.common.domain.Destination;
import dev.travelpilot.common.domain.Pace;
import dev.travelpilot.common.domain.PlaceTag;
import dev.travelpilot.common.domain.PlaceType;
import dev.travelpilot.common.domain.Slot;
import dev.travelpilot.integration.weather.DayWeather;
import dev.travelpilot.integration.weather.Forecast;
import dev.travelpilot.planner.TripPlanDraft.DraftDay;
import dev.travelpilot.planner.TripPlanDraft.DraftItem;
import dev.travelpilot.recommendation.CandidateSelector;
import dev.travelpilot.recommendation.CandidateSet;
import dev.travelpilot.recommendation.HardFilter;
import dev.travelpilot.support.Personas;
import dev.travelpilot.support.TestProps;
import dev.travelpilot.trip.TripRequest;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.api.Test;

class GreedyFallbackPlannerTest {

    private final GreedyFallbackPlanner fallback = new GreedyFallbackPlanner();
    private final PlanValidator validator = new PlanValidator();

    private static PlanContext context(TripRequest req, Forecast forecast) {
        CandidateSet candidates = new CandidateSelector(TestProps.defaults().scoring().weights())
                .select(Personas.CATALOG, req, forecast, HardFilter.Options.defaults());
        return PlanContext.of(req, candidates, forecast, List.of());
    }

    private static Forecast rainEveryDay(TripRequest req) {
        Map<LocalDate, DayWeather> days = new HashMap<>();
        req.dates().forEach(d -> days.put(d, new DayWeather(d, true, 20.0, 24.0, 63, "Mưa")));
        return new Forecast(days);
    }

    static Stream<Arguments> requests() {
        TripRequest lowBudget = new TripRequest(Destination.DANANG, Personas.START, 1, 2, List.of(6), 500_000,
                Set.of(PlaceTag.BEACH), Pace.RELAXED, List.of());
        TripRequest toddler = new TripRequest(Destination.DANANG, Personas.START, 3, 2, List.of(2), 4_000_000,
                Set.of(PlaceTag.FAMILY), Pace.NORMAL, List.of());
        TripRequest fiveDaysThinData = new TripRequest(Destination.QUANGNINH, Personas.START, 5, 2, List.of(),
                3_000_000, Set.of(PlaceTag.NATURE), Pace.NORMAL, List.of());
        TripRequest rainyHanoi = new TripRequest(Destination.HANOI, Personas.START, 3, 2, List.of(), 3_000_000,
                Set.of(PlaceTag.CULTURE), Pace.PACKED, List.of());
        return Stream.of(
                Arguments.of("persona 1 family Da Nang", Personas.family(), Forecast.NONE),
                Arguments.of("persona 2 couple Hanoi", Personas.couple(), Forecast.NONE),
                Arguments.of("persona 3 friends Quang Ninh", Personas.friends(), Forecast.NONE),
                Arguments.of("hard: 500k for a day", lowBudget, Forecast.NONE),
                Arguments.of("hard: 2-year-old", toddler, Forecast.NONE),
                Arguments.of("hard: 5 days in thin Quang Ninh", fiveDaysThinData, Forecast.NONE),
                Arguments.of("hard: rain every day", rainyHanoi, rainEveryDay(rainyHanoi)));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("requests")
    void fallbackAlwaysPassesTheHardRules(String name, TripRequest req, Forecast forecast) {
        PlanContext ctx = context(req, forecast);

        PlanAttempt attempt = fallback.plan(ctx);
        PlanValidator.Report report = validator.validate(attempt.draft(), ctx);

        assertThat(report.hard()).as(name).isEmpty();
        assertThat(attempt.promptVersion()).isEqualTo(GreedyFallbackPlanner.VERSION);
        assertThat(attempt.draft().days()).hasSize(req.days());
        assertThat(attempt.draft().days()).allSatisfy(d -> assertThat(d.items()).isNotEmpty());
    }

    // ---- regressions found by the persona dry run (T2.19) ----

    @Test
    void everyPreferenceGetsItsBestPlaceEvenWhenItScoresLow() {
        PlanContext ctx = context(Personas.family(), Forecast.NONE);
        TripPlanDraft draft = fallback.plan(ctx).draft();
        assertThat(draft.days().stream().flatMap(d -> d.items().stream()).map(DraftItem::placeId))
                .contains(Personas.BA_NA);   // only THEME-PARK place, score 0.38
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("requests")
    void nothingIsScheduledWhenClosed(String name, TripRequest req, Forecast forecast) {
        PlanContext ctx = context(req, forecast);
        PlanValidator.Report report = validator.validate(fallback.plan(ctx).draft(), ctx);
        assertThat(report.soft()).as(name).extracting(Violation::code).doesNotContain("CLOSED_AT_SLOT");
    }

    @Test
    void mealsAvoidCafesWhenRestaurantsAreLeft() {
        for (TripRequest req : List.of(Personas.couple(), Personas.friends())) {
            PlanContext ctx = context(req, Forecast.NONE);
            Map<String, Place> places = ctx.knownPlaces();
            assertThat(fallback.plan(ctx).draft().days().stream().flatMap(d -> d.items().stream())
                    .map(i -> places.get(i.placeId())).filter(p -> p.type() == PlaceType.FOOD))
                    .noneMatch(p -> p.hasTag(PlaceTag.CAFE));
        }
    }

    @Test
    void rainyDaysGetIndoorActivities() {
        TripRequest hanoi = new TripRequest(Destination.HANOI, Personas.START, 2, 2, List.of(), 3_000_000,
                Set.of(PlaceTag.CULTURE), Pace.NORMAL, List.of());
        PlanContext ctx = context(hanoi, rainEveryDay(hanoi));
        Map<String, Place> places = ctx.knownPlaces();

        TripPlanDraft draft = fallback.plan(ctx).draft();

        List<Place> activities = draft.days().stream().flatMap(d -> d.items().stream())
                .map(i -> places.get(i.placeId())).filter(p -> p.type() == PlaceType.ACTIVITY).toList();
        assertThat(activities).isNotEmpty().allMatch(Place::indoor);
        assertThat(draft.days().getFirst().items().getFirst().reason()).contains("trong nhà");
    }

    @Test
    void longVisitTakesTheMorningAndLunchIsOnSite() {
        // With a big budget and THEME-PARK as preference, Bà Nà ranks high enough to be planned.
        TripRequest req = new TripRequest(Destination.DANANG, Personas.START, 1, 2, List.of(), 20_000_000,
                Set.of(PlaceTag.THEME_PARK), Pace.PACKED, List.of());
        PlanContext ctx = context(req, Forecast.NONE);
        TripPlanDraft draft = fallback.plan(ctx).draft();
        DraftDay day = draft.days().getFirst();
        if (day.items().stream().anyMatch(i -> i.placeId().equals(Personas.BA_NA))) {
            DraftItem baNa = day.items().stream().filter(i -> i.placeId().equals(Personas.BA_NA)).findFirst().orElseThrow();
            assertThat(baNa.slot()).isEqualTo(Slot.MORNING);
            assertThat(day.items()).noneMatch(i -> i.slot() == Slot.AFTERNOON);
        }
        assertThat(validator.validate(draft, ctx).hard()).isEmpty();
    }

    @Test
    void afternoonDayTripThatClosesEarlyStartsInTheMorning() {
        // Hội An: 300 min, best time AFTERNOON, open 08:00-17:00 -> only a morning start ends before closing.
        String hoiAn = "danang-node-4601279489";
        TripRequest req = new TripRequest(Destination.DANANG, Personas.START, 3, 4, List.of(), 10_000_000,
                Set.of(PlaceTag.CULTURE, PlaceTag.HISTORY, PlaceTag.PHOTO), Pace.PACKED, List.of());
        PlanContext ctx = context(req, Forecast.NONE);

        TripPlanDraft draft = fallback.plan(ctx).draft();

        DraftItem visit = draft.days().stream().flatMap(d -> d.items().stream())
                .filter(i -> i.placeId().equals(hoiAn)).findFirst().orElseThrow();
        assertThat(visit.slot()).isEqualTo(Slot.MORNING);
        assertThat(validator.validate(draft, ctx).hard()).isEmpty();
    }

    @Test
    void lockedDaysAreKeptAsIs() {
        PlanContext base = PlannerFixtures.context();
        DraftDay lockedDay1 = PlannerFixtures.validDraft().days().getFirst();
        PlanContext ctx = new PlanContext(base.request(), base.candidates(), base.forecast(), base.evidence(),
                Map.of(1, lockedDay1), Map.of());

        TripPlanDraft draft = fallback.plan(ctx).draft();

        assertThat(draft.days().getFirst()).isEqualTo(lockedDay1);
        assertThat(validator.validate(draft, ctx).hard()).isEmpty();
        assertThat(draft.days().subList(1, 3).stream().flatMap(d -> d.items().stream()).map(DraftItem::placeId))
                .doesNotContain(PlannerFixtures.MY_KHE, PlannerFixtures.AEON);   // locked activities are not reused
    }
}
