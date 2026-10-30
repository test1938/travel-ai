package dev.travelpilot.support;

import dev.travelpilot.catalog.CatalogReader;
import dev.travelpilot.catalog.Place;
import dev.travelpilot.common.domain.Destination;
import dev.travelpilot.common.domain.Pace;
import dev.travelpilot.common.domain.PlaceTag;
import dev.travelpilot.trip.TripRequest;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Demo personas from plan/02-architecture.md and the real catalog, for tests. */
public final class Personas {

    /** A Thursday after the Mausoleum reopens. */
    public static final LocalDate START = LocalDate.of(2026, 11, 12);

    public static final List<Place> CATALOG = new CatalogReader().readAll();
    public static final Map<String, Place> BY_ID =
            CATALOG.stream().collect(Collectors.toMap(Place::id, Function.identity()));

    public static final String BA_NA = "danang-way-359114064";
    public static final String MAUSOLEUM = "hanoi-way-37625751";
    public static final String CRAFTSMAN_BAR = "danang-node-7016543285";

    private Personas() {}

    /** Scenario 1: family in Da Nang, 3 days, 2 adults + 6-year-old, 6,000,000 VND, beach + theme park. */
    public static TripRequest family() {
        return new TripRequest(Destination.DANANG, START, 3, 2, List.of(6), 6_000_000,
                Set.of(PlaceTag.BEACH, PlaceTag.THEME_PARK), Pace.RELAXED, List.of());
    }

    /** Scenario 2: couple in Hanoi, 2 days, 3,000,000 VND, street food + photo + culture. */
    public static TripRequest couple() {
        return new TripRequest(Destination.HANOI, START, 2, 2, List.of(), 3_000_000,
                Set.of(PlaceTag.STREET_FOOD, PlaceTag.PHOTO, PlaceTag.CULTURE), Pace.RELAXED, List.of());
    }

    /** Scenario 3': four friends in Quang Ninh, 2 days, 8,000,000 VND, nature + adventure. */
    public static TripRequest friends() {
        return new TripRequest(Destination.QUANGNINH, START, 2, 4, List.of(), 8_000_000,
                Set.of(PlaceTag.NATURE, PlaceTag.ADVENTURE), Pace.NORMAL, List.of());
    }

    public static List<Place> placesOf(Destination destination) {
        return CATALOG.stream().filter(p -> p.destination() == destination).toList();
    }

    public static Place place(String id) {
        return BY_ID.get(id);
    }
}
