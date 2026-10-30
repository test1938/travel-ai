package dev.travelpilot.integration.weather;

import java.time.LocalDate;
import java.util.Map;

/** Weather for the trip dates; days without data are simply absent (unknown, treated as dry). */
public record Forecast(Map<LocalDate, DayWeather> days) {

    public static final Forecast NONE = new Forecast(Map.of());

    public Forecast {
        days = Map.copyOf(days);
    }

    public boolean isRainy(LocalDate date) {
        DayWeather w = days.get(date);
        return w != null && w.rainy();
    }

    public boolean anyRain() {
        return days.values().stream().anyMatch(DayWeather::rainy);
    }

    public boolean isEmpty() {
        return days.isEmpty();
    }
}
