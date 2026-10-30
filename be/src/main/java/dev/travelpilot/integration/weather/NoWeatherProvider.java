package dev.travelpilot.integration.weather;

import dev.travelpilot.common.domain.Destination;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Component;

/** Used when travelpilot.weather.enabled=false (tests, offline demos): always "no data". */
@Component
public class NoWeatherProvider implements WeatherProvider {

    @Override
    public Forecast forecast(Destination destination, List<LocalDate> dates) {
        return Forecast.NONE;
    }
}
