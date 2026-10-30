package dev.travelpilot.planner;

import static org.assertj.core.api.Assertions.assertThat;

import dev.travelpilot.catalog.Place;
import dev.travelpilot.common.domain.PlaceType;
import dev.travelpilot.integration.weather.DayWeather;
import dev.travelpilot.integration.weather.Forecast;
import dev.travelpilot.integration.weather.WeatherProvider;
import dev.travelpilot.support.Personas;
import dev.travelpilot.support.StubChatModel;
import dev.travelpilot.trip.TripPlan;
import dev.travelpilot.trip.TripRequest;
import dev.travelpilot.trip.Warning;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** T5.02: the forecast reaches candidates, rules and the fallback; missing days are flagged. */
class WeatherInPlanningTest {

    private static final TripRequest FAMILY = Personas.family();

    /** Every trip day dry, except the given day which is rainy. */
    private static WeatherProvider weather(Integer rainyDay) {
        return (dest, dates) -> {
            Map<LocalDate, DayWeather> days = new HashMap<>();
            for (int i = 0; i < dates.size(); i++) {
                boolean rainy = rainyDay != null && rainyDay == i + 1;
                days.put(dates.get(i), new DayWeather(dates.get(i), rainy, 24.0, 29.0, rainy ? 63 : 1,
                        rainy ? "Mưa (80% mưa)" : "Ít mây"));
            }
            return new Forecast(days);
        };
    }

    /** The model is down, so the deterministic fallback plans: the weather effect is reproducible. */
    private static TripPlan planWith(WeatherProvider weather) {
        StubChatModel model = new StubChatModel().fail(new RuntimeException("quota"));
        return OfflinePlanning.orchestrator(model, OfflinePlanning.places(), weather).plan(FAMILY).plan();
    }

    private static long indoorActivities(TripPlan plan, int dayIndex) {
        return plan.days().get(dayIndex - 1).items().stream()
                .map(i -> Personas.BY_ID.get(i.placeId()))
                .filter(p -> p != null && p.type() == PlaceType.ACTIVITY)
                .filter(Place::indoor)
                .count();
    }

    private static long outdoorActivities(TripPlan plan, int dayIndex) {
        return plan.days().get(dayIndex - 1).items().stream()
                .map(i -> Personas.BY_ID.get(i.placeId()))
                .filter(p -> p != null && p.type() == PlaceType.ACTIVITY && !p.indoor())
                .count();
    }

    @Test
    void rainOnDayTwoMovesThatDayIndoors() {
        TripPlan dry = planWith(weather(null));
        TripPlan rainy = planWith(weather(2));

        assertThat(rainy.days().get(1).weather().rainy()).isTrue();
        assertThat(indoorActivities(rainy, 2)).isGreaterThan(indoorActivities(dry, 2));
        assertThat(outdoorActivities(rainy, 2)).isLessThan(outdoorActivities(dry, 2));
        assertThat(rainy.warnings()).extracting(Warning::code).doesNotContain("WEATHER_UNAVAILABLE");
    }

    @Test
    void partlyCoveredTripWarnsPerMissingDay() {
        WeatherProvider onlyFirstDay = (dest, dates) -> new Forecast(Map.of(dates.getFirst(),
                new DayWeather(dates.getFirst(), false, 24.0, 29.0, 1, "Ít mây")));

        TripPlan plan = planWith(onlyFirstDay);

        assertThat(plan.warnings()).filteredOn(w -> w.code().equals("WEATHER_UNAVAILABLE"))
                .extracting(Warning::dayIndex).containsExactly(2, 3);
    }

    @Test
    void noForecastAtAllGivesOneTripWarning() {
        assertThat(PlannerOrchestrator.weatherWarnings(FAMILY, Forecast.NONE))
                .singleElement()
                .satisfies(w -> assertThat(w.dayIndex()).isNull());
    }
}
