package dev.travelpilot.integration.weather;

import java.time.LocalDate;

/** Weather of one day; {@code rainy} drives the indoor preference. Temperatures may be null. */
public record DayWeather(LocalDate date, boolean rainy, Double tempMinC, Double tempMaxC, Integer weatherCode,
        String summary) {}
