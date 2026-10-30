package dev.travelpilot.tools;

import dev.travelpilot.catalog.Place;
import dev.travelpilot.catalog.PlaceRepository;
import dev.travelpilot.common.Vnd;
import dev.travelpilot.common.domain.Destination;
import dev.travelpilot.common.domain.PlaceTag;
import dev.travelpilot.integration.inventory.Availability;
import dev.travelpilot.integration.inventory.PartnerInventoryProvider;
import dev.travelpilot.integration.weather.DayWeather;
import dev.travelpilot.integration.weather.Forecast;
import dev.travelpilot.integration.weather.OpenMeteoClient;
import dev.travelpilot.integration.weather.WeatherProvider;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.format.TextStyle;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * Read-only tools the chat may call (plan/03-ai-pipeline.md §8). Every argument is validated here and a bad one
 * throws {@link IllegalArgumentException}, which the tool loop reports back to the model as a rejected call.
 * Coordinates, URLs and prices never come from the model: only enum names, dates, counts and catalog ids.
 */
@Component
public class TravelTools {

    public static final String WEATHER = "getWeatherForecast";
    public static final String SEARCH = "searchPlaces";
    public static final String AVAILABILITY = "checkAvailability";

    static final int MAX_FORECAST_DAYS = 7;
    static final int MAX_PLACES = 10;
    static final int MAX_GUESTS = 20;
    static final int MAX_DAYS_AHEAD = 365;
    private static final Locale VI = Locale.forLanguageTag("vi");

    /** Citation shown for a tool result: title, url, license. */
    public record ToolSource(String title, String url, String license) {}

    /** Source metadata per tool, used when a result is cited in an answer. */
    public static final Map<String, ToolSource> SOURCES = Map.of(
            WEATHER, new ToolSource("Dự báo thời tiết Open-Meteo", "https://open-meteo.com/", "CC BY 4.0"),
            SEARCH, new ToolSource("Danh mục địa điểm TravelPilot", null, "ODbL (OpenStreetMap)"),
            AVAILABILITY, new ToolSource("Tình trạng chỗ (dữ liệu mô phỏng)", null, "demo"));

    private final PlaceRepository places;
    private final WeatherProvider weather;
    private final PartnerInventoryProvider inventory;
    private final Clock clock;

    public TravelTools(PlaceRepository places, WeatherProvider weather, PartnerInventoryProvider inventory,
            Clock clock) {
        this.places = places;
        this.weather = weather;
        this.inventory = inventory;
        this.clock = clock;
    }

    @Tool(name = WEATHER, resultConverter = PlainTextResult.class,
            description = "Daily weather forecast (Open-Meteo) for a destination: rain or not, summary and "
                    + "temperatures. Only today and the next 15 days are available.")
    public String getWeatherForecast(
            @ToolParam(description = "Destination: HANOI, DANANG or QUANGNINH") String destination,
            @ToolParam(description = "First day, ISO format yyyy-MM-dd") String from,
            @ToolParam(description = "Number of days, 1 to 7") int days) {
        Destination dest = destination(destination);
        LocalDate start = date(from, "from");
        LocalDate today = LocalDate.now(clock);
        require(days >= 1 && days <= MAX_FORECAST_DAYS, "days must be between 1 and " + MAX_FORECAST_DAYS);
        LocalDate lastForecastDay = today.plusDays(OpenMeteoClient.FORECAST_DAYS - 1);
        require(!start.isBefore(today) && !start.isAfter(lastForecastDay),
                "from must be between " + today + " and " + lastForecastDay + " (forecast range)");

        List<LocalDate> dates = start.datesUntil(start.plusDays(days)).toList();
        Forecast forecast = weather.forecast(dest, dates);
        StringBuilder sb = new StringBuilder("Dự báo thời tiết " + dest.displayName() + " (lấy ngày " + today + "):");
        for (LocalDate d : dates) {
            sb.append("\n- ").append(d).append(" (").append(d.getDayOfWeek().getDisplayName(TextStyle.FULL, VI))
                    .append("): ").append(describe(forecast.days().get(d)));
        }
        return sb.toString();
    }

    @Tool(name = SEARCH, resultConverter = PlainTextResult.class,
            description = "Search the curated catalog of a destination by tags, indoor/outdoor and the youngest "
                    + "child's age. Returns at most 10 places, most popular first, with their ids.")
    public String searchPlaces(
            @ToolParam(description = "Destination: HANOI, DANANG or QUANGNINH") String destination,
            @ToolParam(required = false, description = "Tags such as BEACH, MUSEUM, THEME-PARK, FOOD; a place "
                    + "matches if it has any of them") List<String> tags,
            @ToolParam(required = false, description = "true = indoor only, false = outdoor only") Boolean indoor,
            @ToolParam(required = false, description = "Age of the youngest child; only places that allow it")
            Integer maxChildAge) {
        Destination dest = destination(destination);
        Set<PlaceTag> wanted = tags == null ? Set.of() : tags.stream().map(TravelTools::tag).collect(Collectors.toSet());
        require(maxChildAge == null || (maxChildAge >= 0 && maxChildAge <= 17), "maxChildAge must be 0..17");

        List<Place> found = places.findByDestination(dest).stream()
                .filter(p -> wanted.isEmpty() || p.tags().stream().anyMatch(wanted::contains))
                .filter(p -> indoor == null || p.indoor() == indoor)
                .filter(p -> maxChildAge == null || p.childMinAge() <= maxChildAge)
                .sorted(Comparator.comparingDouble(Place::popularity).reversed())
                .limit(MAX_PLACES)
                .toList();
        if (found.isEmpty()) {
            return "Không tìm thấy địa điểm phù hợp ở " + dest.displayName() + ".";
        }
        StringBuilder sb = new StringBuilder("Địa điểm ở " + dest.displayName() + " (" + found.size() + "):");
        for (Place p : found) {
            sb.append("\n- ").append(p.name()).append(" [").append(p.id()).append("] · ")
                    .append(p.tags().stream().map(PlaceTag::code).collect(Collectors.joining(",")))
                    .append(" · ").append(p.indoor() ? "trong nhà" : "ngoài trời")
                    .append(" · vé người lớn ").append(price(p))
                    .append(" · trẻ em từ ").append(p.childMinAge()).append(" tuổi")
                    .append(p.reviewFields().contains("childMinAge") || p.isEstimated("childMinAge")
                            ? " (chưa xác minh)" : "");
        }
        return sb.toString();
    }

    @Tool(name = AVAILABILITY, resultConverter = PlainTextResult.class,
            description = "Check whether a catalog place has room on a date for a group. Simulated partner data "
                    + "(demo): always say so when using it.")
    public String checkAvailability(
            @ToolParam(description = "Catalog place id from the plan or from searchPlaces") String placeId,
            @ToolParam(description = "Date, ISO format yyyy-MM-dd") String date,
            @ToolParam(description = "Number of guests, 1 to 20") int guests) {
        require(placeId != null && !placeId.isBlank(), "placeId is required");
        Place place = places.findById(placeId.strip()).orElseThrow(() -> new IllegalArgumentException(
                "Unknown placeId '" + placeId + "': use an id from the plan or from searchPlaces"));
        LocalDate day = date(date, "date");
        LocalDate today = LocalDate.now(clock);
        require(!day.isBefore(today) && !day.isAfter(today.plusDays(MAX_DAYS_AHEAD)),
                "date must be between " + today + " and " + today.plusDays(MAX_DAYS_AHEAD));
        require(guests >= 1 && guests <= MAX_GUESTS, "guests must be between 1 and " + MAX_GUESTS);

        Availability a = inventory.check(place.id(), day, guests);
        return "Tình trạng chỗ " + place.name() + " ngày " + day + " cho " + guests + " khách: "
                + (a.available() ? "còn chỗ (còn " + a.remaining() + ")" : "hết chỗ (còn " + a.remaining() + ")")
                + ". " + a.note();
    }

    private static String describe(DayWeather w) {
        if (w == null) {
            return "chưa có dự báo";
        }
        String temp = w.tempMinC() != null && w.tempMaxC() != null
                ? ", " + Math.round(w.tempMinC()) + "–" + Math.round(w.tempMaxC()) + "°C"
                : "";
        return w.summary() + temp + (w.rainy() ? " → có mưa, nên ưu tiên hoạt động trong nhà" : "");
    }

    private static String price(Place p) {
        if (p.estCostVnd() == 0) {
            return "miễn phí";
        }
        return (p.isPriceEstimated() ? "khoảng " : "") + Vnd.format(p.estCostVnd());
    }

    private static Destination destination(String value) {
        require(value != null && !value.isBlank(), "destination is required (HANOI, DANANG or QUANGNINH)");
        try {
            return Destination.valueOf(value.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown destination '" + value + "': use HANOI, DANANG or QUANGNINH");
        }
    }

    private static PlaceTag tag(String value) {
        try {
            return PlaceTag.fromCode(value);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown tag '" + value + "'");
        }
    }

    private static LocalDate date(String value, String name) {
        require(value != null && !value.isBlank(), name + " is required (yyyy-MM-dd)");
        try {
            return LocalDate.parse(value.strip());
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException(name + " must be an ISO date yyyy-MM-dd, got '" + value + "'");
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }
}
