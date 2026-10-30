package dev.travelpilot.common.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Place tags exactly as in the dataset. Some codes contain a hyphen (THEME-PARK, CABLE-CAR, STREET-FOOD):
 * the Java constant uses an underscore and {@link #code()} is the JSON / database value.
 */
public enum PlaceTag {
    BEACH, NATURE, MOUNTAIN, ISLAND, CAVE, RIVER, WATER, CULTURE, HISTORY, MUSEUM, TEMPLE, ART,
    LANDMARK, VILLAGE, CRAFT, FAMILY, KIDS, THEME_PARK, CABLE_CAR, SHOW, FOOD, SEAFOOD, STREET_FOOD,
    LOCAL, CAFE, MARKET, SHOPPING, NIGHTLIFE, NIGHT, PHOTO, ROMANTIC, RELAX, SPA, SIGHTSEEING,
    ADVENTURE, HIKING, KAYAK, BOAT;

    private static final Map<String, PlaceTag> BY_CODE =
            Arrays.stream(values()).collect(Collectors.toUnmodifiableMap(PlaceTag::code, Function.identity()));

    /** Value used in JSON, in the database and in URLs, e.g. "THEME-PARK". */
    @JsonValue
    public String code() {
        return name().replace('_', '-');
    }

    /** Accepts the dataset code ("THEME-PARK") and, for convenience, the Java name ("THEME_PARK"). */
    @JsonCreator
    public static PlaceTag fromCode(String code) {
        if (code == null) {
            throw new IllegalArgumentException("Place tag is null");
        }
        PlaceTag tag = BY_CODE.get(code.trim().toUpperCase().replace('_', '-'));
        if (tag == null) {
            throw new IllegalArgumentException("Unknown place tag: " + code);
        }
        return tag;
    }
}
