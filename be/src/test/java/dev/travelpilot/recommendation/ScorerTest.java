package dev.travelpilot.recommendation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import dev.travelpilot.catalog.Place;
import dev.travelpilot.common.domain.BestTime;
import dev.travelpilot.common.domain.Destination;
import dev.travelpilot.common.domain.Pace;
import dev.travelpilot.common.domain.PlaceTag;
import dev.travelpilot.integration.weather.DayWeather;
import dev.travelpilot.integration.weather.Forecast;
import dev.travelpilot.recommendation.ScoreComponents.BudgetFit;
import dev.travelpilot.recommendation.ScoreComponents.DistanceFit;
import dev.travelpilot.recommendation.ScoreComponents.GroupFit;
import dev.travelpilot.recommendation.ScoreComponents.Popularity;
import dev.travelpilot.recommendation.ScoreComponents.PreferenceMatch;
import dev.travelpilot.recommendation.ScoreComponents.ScheduleFit;
import dev.travelpilot.support.Personas;
import dev.travelpilot.support.TestPlace;
import dev.travelpilot.support.TestProps;
import dev.travelpilot.trip.TripRequest;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ScorerTest {

    private static ScoreComponent.Context ctx(TripRequest req) {
        return new ScoreComponent.Context(req, Forecast.NONE);
    }

    private static TripRequest req(int adults, List<Integer> kids, long budget, Set<PlaceTag> prefs, Pace pace) {
        return new TripRequest(Destination.DANANG, Personas.START, 2, adults, kids, budget, prefs, pace, List.of());
    }

    // ---- PreferenceMatch ----

    @Test
    void preferenceIsShareOfMatchedPreferences() {
        Place beachPark = TestPlace.activity("a").tags(PlaceTag.BEACH, PlaceTag.FAMILY).build();
        TripRequest two = req(2, List.of(), 5_000_000, Set.of(PlaceTag.BEACH, PlaceTag.THEME_PARK), Pace.NORMAL);
        assertThat(new PreferenceMatch().score(beachPark, ctx(two))).isEqualTo(0.5);
    }

    @Test
    void noPreferencesIsNeutral() {
        assertThat(new PreferenceMatch().score(TestPlace.activity("a").build(),
                ctx(req(2, List.of(), 5_000_000, Set.of(), Pace.NORMAL)))).isEqualTo(0.5);
    }

    // ---- BudgetFit ----

    @Test
    void freeOrCheapPlaceGetsFullBudgetScore() {
        TripRequest r = req(2, List.of(), 6_000_000, Set.of(), Pace.NORMAL);   // per activity 600,000
        assertThat(new BudgetFit().score(TestPlace.activity("free").build(), ctx(r))).isEqualTo(1);
        assertThat(new BudgetFit().score(TestPlace.activity("cheap").cost(150_000, 0).build(), ctx(r))).isEqualTo(1);
    }

    @Test
    void budgetScoreFallsLinearlyToZeroAt150Percent() {
        TripRequest r = req(2, List.of(), 6_000_000, Set.of(), Pace.NORMAL);   // per activity 600,000
        // group cost 600,000 = 1.0x per item -> (900k - 600k) / 600k = 0.5
        assertThat(new BudgetFit().score(TestPlace.activity("mid").cost(300_000, 0).build(), ctx(r)))
                .isCloseTo(0.5, within(1e-9));
        assertThat(new BudgetFit().score(TestPlace.activity("exp").cost(450_000, 0).build(), ctx(r))).isZero();
    }

    @Test
    void mealsAreJudgedAgainstTheMealBudget() {
        TripRequest r = req(2, List.of(), 2_000_000, Set.of(), Pace.NORMAL);   // per meal 200,000
        assertThat(new BudgetFit().score(TestPlace.food("pho").cost(50_000, 0).build(), ctx(r))).isEqualTo(1);
        assertThat(new BudgetFit().score(TestPlace.food("lobster").cost(500_000, 0).build(), ctx(r))).isZero();
    }

    // ---- DistanceFit ----

    @Test
    void distanceScoreDecreasesWithDistance() {
        TripRequest r = req(2, List.of(), 5_000_000, Set.of(), Pace.NORMAL);
        Place center = TestPlace.activity("c").build();
        Place far = TestPlace.activity("f").at(15.80, 108.22).build();   // about 28 km south
        assertThat(new DistanceFit().score(center, ctx(r))).isCloseTo(1, within(1e-9));
        assertThat(new DistanceFit().score(far, ctx(r))).isZero();
    }

    @Test
    void dayTripsKeepAFloor() {
        Place baNa = Personas.place(Personas.BA_NA);
        assertThat(new DistanceFit().score(baNa, ctx(Personas.family()))).isEqualTo(DistanceFit.DAY_TRIP_FLOOR);
    }

    // ---- ScheduleFit ----

    @Test
    void relaxedPacePrefersShortVisits() {
        TripRequest relaxed = req(2, List.of(), 5_000_000, Set.of(), Pace.RELAXED);
        ScheduleFit fit = new ScheduleFit();
        assertThat(fit.score(TestPlace.activity("s").duration(120).bestTime(BestTime.MORNING).build(), ctx(relaxed))).isEqualTo(1);
        assertThat(fit.score(TestPlace.activity("m").duration(240).bestTime(BestTime.MORNING).build(), ctx(relaxed))).isEqualTo(0.6);
        assertThat(fit.score(TestPlace.activity("l").duration(480).bestTime(BestTime.MORNING).build(), ctx(relaxed))).isEqualTo(0.3);
    }

    @Test
    void packedPaceAcceptsLongVisitsAndAnyTimeGetsBonus() {
        ScheduleFit fit = new ScheduleFit();
        assertThat(fit.score(TestPlace.activity("l").duration(480).bestTime(BestTime.MORNING).build(),
                ctx(req(2, List.of(), 5_000_000, Set.of(), Pace.PACKED)))).isEqualTo(1);
        assertThat(fit.score(TestPlace.activity("l").duration(400).build(),
                ctx(req(2, List.of(), 5_000_000, Set.of(), Pace.NORMAL)))).isCloseTo(0.7, within(1e-9));
    }

    // ---- GroupFit ----

    @Test
    void familiesPreferFamilyPlacesAndAvoidNightlife() {
        TripRequest family = Personas.family();
        GroupFit fit = new GroupFit();
        assertThat(fit.score(TestPlace.activity("k").tags(PlaceTag.KIDS).build(), ctx(family))).isEqualTo(1);
        assertThat(fit.score(TestPlace.activity("n").tags(PlaceTag.NIGHTLIFE).build(), ctx(family))).isZero();
        assertThat(fit.score(TestPlace.activity("o").childMinAge(12).build(), ctx(family))).isZero();
        assertThat(fit.score(TestPlace.activity("x").build(), ctx(family))).isEqualTo(0.5);
    }

    @Test
    void couplesAndFriendsPreferDifferentPlaces() {
        GroupFit fit = new GroupFit();
        Place romantic = TestPlace.activity("r").tags(PlaceTag.ROMANTIC).build();
        Place boat = TestPlace.activity("b").tags(PlaceTag.BOAT).build();
        assertThat(fit.score(romantic, ctx(Personas.couple()))).isEqualTo(1);
        assertThat(fit.score(boat, ctx(Personas.couple()))).isEqualTo(0.5);
        assertThat(fit.score(boat, ctx(Personas.friends()))).isEqualTo(1);
        assertThat(fit.score(boat, ctx(req(1, List.of(), 1_000_000, Set.of(), Pace.NORMAL)))).isEqualTo(0.5);
    }

    // ---- Popularity + Scorer ----

    @Test
    void popularityIsTakenFromData() {
        assertThat(new Popularity().score(TestPlace.activity("p").popularity(0.9).build(),
                ctx(Personas.family()))).isEqualTo(0.9);
    }

    @Test
    void totalIsWeightedSumOfComponents() {
        Scorer scorer = new Scorer(TestProps.defaults().scoring().weights());
        ScoreBreakdown s = scorer.score(Personas.place(Personas.BA_NA), ctx(Personas.family()));

        double expected = 0.30 * s.preference() + 0.20 * s.budget() + 0.15 * s.distance() + 0.15 * s.schedule()
                + 0.10 * s.group() + 0.10 * s.popularity();
        assertThat(s.total()).isCloseTo(expected, within(0.002));
        assertThat(s.preference()).isEqualTo(0.5);   // tags match THEME-PARK but not BEACH
        assertThat(s.group()).isEqualTo(1);          // FAMILY / KIDS with a child on the trip
        assertThat(s.rainBonus()).isZero();
    }

    @Test
    void indoorPlacesGetABonusWhenRainIsForecast() {
        Scorer scorer = new Scorer(TestProps.defaults().scoring().weights());
        Place museum = TestPlace.activity("m").indoor().build();
        Forecast rain = new Forecast(Map.of(Personas.START,
                new DayWeather(Personas.START, true, null, null, 63, "Mưa")));

        double dry = scorer.score(museum, ctx(Personas.family())).total();
        ScoreBreakdown wet = scorer.score(museum, new ScoreComponent.Context(Personas.family(), rain));

        assertThat(wet.rainBonus()).isEqualTo(Scorer.RAIN_INDOOR_BONUS);
        assertThat(wet.total()).isCloseTo(dry + Scorer.RAIN_INDOOR_BONUS, within(0.002));
    }
}
