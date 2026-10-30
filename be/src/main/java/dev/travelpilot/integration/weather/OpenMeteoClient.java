package dev.travelpilot.integration.weather;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import dev.travelpilot.common.config.TravelPilotProperties;
import dev.travelpilot.common.domain.Destination;
import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Open-Meteo daily forecast (free, no key). Coordinates come from the {@link Destination} enum, never from user or
 * model input. One request covers the next 16 days of a destination and is cached; dates outside that window are
 * simply absent. Any failure returns {@link Forecast#NONE} (failures are not cached).
 */
@Primary
@Component
@ConditionalOnProperty(prefix = "travelpilot.weather", name = "enabled", havingValue = "true")
public class OpenMeteoClient implements WeatherProvider {

    /** Open-Meteo's maximum forecast range. */
    public static final int FORECAST_DAYS = 16;
    static final int RAIN_PROBABILITY_PERCENT = 60;
    private static final Logger log = LoggerFactory.getLogger(OpenMeteoClient.class);

    private final RestClient http;
    private final Clock clock;
    private final Cache<Key, Map<LocalDate, DayWeather>> cache;

    /** Cache key: the fetch day is part of it, so a new day always gets a fresh forecast. */
    private record Key(Destination destination, LocalDate fetchedOn) {}

    @Autowired
    public OpenMeteoClient(TravelPilotProperties props, Clock clock) {
        this(RestClient.builder().requestFactory(requestFactory(props.weather())), props, clock);
    }

    OpenMeteoClient(RestClient.Builder builder, TravelPilotProperties props, Clock clock) {
        this.http = builder.baseUrl(props.weather().baseUrl()).build();
        this.clock = clock;
        this.cache = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofMinutes(props.weather().cacheMinutes()))
                .maximumSize(64)
                .build();
    }

    private static JdkClientHttpRequestFactory requestFactory(TravelPilotProperties.Weather cfg) {
        Duration timeout = Duration.ofSeconds(cfg.timeoutSeconds());
        JdkClientHttpRequestFactory factory =
                new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(timeout).build());
        factory.setReadTimeout(timeout);
        return factory;
    }

    @Override
    public Forecast forecast(Destination destination, List<LocalDate> dates) {
        Map<LocalDate, DayWeather> all = daily(destination);
        Map<LocalDate, DayWeather> picked = new LinkedHashMap<>();
        for (LocalDate d : dates) {
            DayWeather w = all.get(d);
            if (w != null) {
                picked.put(d, w);
            }
        }
        return new Forecast(picked);
    }

    /** The whole 16-day forecast for a destination, from cache when possible; empty on failure. */
    private Map<LocalDate, DayWeather> daily(Destination destination) {
        Key key = new Key(destination, LocalDate.now(clock));
        Map<LocalDate, DayWeather> cached = cache.getIfPresent(key);
        if (cached != null) {
            return cached;
        }
        long start = System.nanoTime();
        try {
            Response body = http.get()
                    .uri(uri -> uri.path("/v1/forecast")
                            .queryParam("latitude", destination.centerLat())
                            .queryParam("longitude", destination.centerLon())
                            .queryParam("daily", "weather_code,precipitation_probability_max,"
                                    + "temperature_2m_max,temperature_2m_min")
                            .queryParam("timezone", Destination.ZONE.getId())
                            .queryParam("forecast_days", FORECAST_DAYS)
                            .build())
                    .retrieve()
                    .body(Response.class);
            Map<LocalDate, DayWeather> days = parse(body);
            log.info("Open-Meteo {}: {} days in {} ms", destination, days.size(),
                    Duration.ofNanos(System.nanoTime() - start).toMillis());
            if (!days.isEmpty()) {
                cache.put(key, days);
            }
            return days;
        } catch (RuntimeException e) {
            log.warn("Open-Meteo {} failed after {} ms: {}", destination,
                    Duration.ofNanos(System.nanoTime() - start).toMillis(), e.toString());
            return Map.of();
        }
    }

    /** Maps the daily arrays; days with a missing date are skipped, missing values stay null. */
    static Map<LocalDate, DayWeather> parse(Response body) {
        if (body == null || body.daily() == null || body.daily().time() == null) {
            return Map.of();
        }
        Daily d = body.daily();
        Map<LocalDate, DayWeather> out = new HashMap<>();
        for (int i = 0; i < d.time().size(); i++) {
            String time = d.time().get(i);
            if (time == null) {
                continue;
            }
            Integer code = at(d.weatherCode(), i);
            Integer rainChance = at(d.precipitationProbabilityMax(), i);
            LocalDate date = LocalDate.parse(time);
            out.put(date, new DayWeather(date, isRainy(code, rainChance), at(d.temperatureMin(), i),
                    at(d.temperatureMax(), i), code, summary(code, rainChance)));
        }
        return Map.copyOf(out);
    }

    /** Rain: probability >= 60 %, or a WMO code for drizzle/rain (51-67), showers (80-82) or thunderstorm (95-99). */
    static boolean isRainy(Integer wmoCode, Integer rainChancePercent) {
        if (rainChancePercent != null && rainChancePercent >= RAIN_PROBABILITY_PERCENT) {
            return true;
        }
        if (wmoCode == null) {
            return false;
        }
        int c = wmoCode;
        return (c >= 51 && c <= 67) || (c >= 80 && c <= 82) || (c >= 95 && c <= 99);
    }

    /** Short Vietnamese description shown on the day card, e.g. "Mưa rào (70%)". */
    static String summary(Integer wmoCode, Integer rainChancePercent) {
        String text = wmoCode == null ? "Không rõ" : switch (wmoCode) {
            case 0 -> "Trời quang";
            case 1, 2 -> "Ít mây";
            case 3 -> "Nhiều mây";
            case 45, 48 -> "Sương mù";
            case 51, 53, 55, 56, 57 -> "Mưa phùn";
            case 61, 63, 65, 66, 67 -> "Mưa";
            case 80, 81, 82 -> "Mưa rào";
            case 95, 96, 99 -> "Dông";
            default -> "Thời tiết thay đổi";
        };
        return rainChancePercent == null ? text : text + " (" + rainChancePercent + "% mưa)";
    }

    private static <T> T at(List<T> values, int i) {
        return values == null || i >= values.size() ? null : values.get(i);
    }

    /** The subset of the Open-Meteo response we read. */
    record Response(Daily daily) {}

    record Daily(
            List<String> time,
            @JsonProperty("weather_code") List<Integer> weatherCode,
            @JsonProperty("precipitation_probability_max") List<Integer> precipitationProbabilityMax,
            @JsonProperty("temperature_2m_max") List<Double> temperatureMax,
            @JsonProperty("temperature_2m_min") List<Double> temperatureMin) {}
}
