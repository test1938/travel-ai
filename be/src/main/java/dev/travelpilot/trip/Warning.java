package dev.travelpilot.trip;

/** User-facing note attached to a plan; dayIndex and placeId are optional. */
public record Warning(String code, String message, Integer dayIndex, String placeId) {

    public static Warning of(String code, String message) {
        return new Warning(code, message, null, null);
    }
}
