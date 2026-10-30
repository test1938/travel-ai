package dev.travelpilot.catalog;

import dev.travelpilot.common.domain.BestTime;
import dev.travelpilot.common.domain.Destination;
import dev.travelpilot.common.domain.PlaceTag;
import dev.travelpilot.common.domain.PlaceType;
import dev.travelpilot.common.domain.PriceLevel;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** One element of catalog_*.json exactly as written by the data pipeline; any field may be missing. */
record PlaceJson(
        String id,
        Destination destination,
        PlaceType type,
        String name,
        String nameEn,
        String description,
        Boolean curated,
        String osmId,
        List<PlaceTag> tags,
        Long estCostVnd,
        Long childCostVnd,
        PriceLevel priceLevel,
        String priceNote,
        String priceSourceUrl,
        LocalDate verifyAt,
        Integer durationMin,
        Integer childMinAge,
        Boolean indoor,
        BestTime bestTime,
        String openingHours,
        Double lat,
        Double lon,
        Double popularity,
        Boolean dayTrip,
        String sourceUrl,
        List<String> estimatedFields,
        List<String> reviewFields,
        Map<String, String> fieldSources) {

    /** Names of fields a curated place must have; the planner relies on all of them. */
    List<String> missingRequiredFields() {
        Map<String, Object> required = new LinkedHashMap<>();
        required.put("id", id);
        required.put("destination", destination);
        required.put("type", type);
        required.put("name", name);
        required.put("description", description);
        required.put("tags", tags);
        required.put("estCostVnd", estCostVnd);
        required.put("childCostVnd", childCostVnd);
        required.put("priceLevel", priceLevel);
        required.put("priceNote", priceNote);
        required.put("durationMin", durationMin);
        required.put("childMinAge", childMinAge);
        required.put("indoor", indoor);
        required.put("bestTime", bestTime);
        required.put("openingHours", openingHours);
        required.put("lat", lat);
        required.put("lon", lon);
        required.put("popularity", popularity);
        required.put("dayTrip", dayTrip);
        List<String> missing = new ArrayList<>();
        required.forEach((field, value) -> {
            if (value == null) {
                missing.add(field);
            }
        });
        return missing;
    }

    /** Converts to the domain record; call only when {@link #missingRequiredFields()} is empty. */
    Place toPlace() {
        return new Place(id, destination, type, name, nameEn, description, Boolean.TRUE.equals(curated), osmId,
                tags, estCostVnd, childCostVnd, priceLevel, priceNote, priceSourceUrl, verifyAt, durationMin,
                childMinAge, indoor, bestTime, openingHours, lat, lon, popularity, dayTrip, sourceUrl,
                orEmpty(estimatedFields), orEmpty(reviewFields), fieldSources == null ? Map.of() : fieldSources);
    }

    private static List<String> orEmpty(List<String> list) {
        return list == null ? List.of() : list;
    }
}
