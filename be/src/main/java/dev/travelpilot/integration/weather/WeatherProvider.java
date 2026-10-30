package dev.travelpilot.integration.weather;

import dev.travelpilot.common.domain.Destination;
import java.time.LocalDate;
import java.util.List;

/**
 * Port for weather data. Implementations must not throw for provider problems: return {@link Forecast#NONE}
 * (the planner then adds a WEATHER_UNAVAILABLE warning) so a weather outage never blocks planning.
 */
public interface WeatherProvider {

    Forecast forecast(Destination destination, List<LocalDate> dates);
}
