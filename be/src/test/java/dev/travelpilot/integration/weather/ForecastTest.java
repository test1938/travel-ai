package dev.travelpilot.integration.weather;

import static org.assertj.core.api.Assertions.assertThat;

import dev.travelpilot.common.domain.Destination;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ForecastTest {

    static final LocalDate D1 = LocalDate.of(2026, 11, 12);

    @Test
    void noWeatherProviderReturnsEmptyForecast() {
        Forecast f = new NoWeatherProvider().forecast(Destination.DANANG, List.of(D1, D1.plusDays(1)));
        assertThat(f.isEmpty()).isTrue();
        assertThat(f.isRainy(D1)).isFalse();
        assertThat(f.anyRain()).isFalse();
    }

    @Test
    void rainyDaysAreReported() {
        Forecast f = new Forecast(Map.of(
                D1, new DayWeather(D1, false, 22.0, 28.0, 1, "Nắng"),
                D1.plusDays(1), new DayWeather(D1.plusDays(1), true, 21.0, 25.0, 63, "Mưa vừa")));
        assertThat(f.isRainy(D1)).isFalse();
        assertThat(f.isRainy(D1.plusDays(1))).isTrue();
        assertThat(f.anyRain()).isTrue();
    }
}
