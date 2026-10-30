package dev.travelpilot.catalog;

import dev.travelpilot.common.domain.BestTime;
import dev.travelpilot.common.domain.Destination;
import dev.travelpilot.common.domain.PlaceTag;
import dev.travelpilot.common.domain.PlaceType;
import dev.travelpilot.common.domain.PriceLevel;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * A curated place: the single source of truth for prices, hours, duration and age limits
 * (see plan/01-dataset.md §3). Optional fields: nameEn, osmId, priceSourceUrl, verifyAt, sourceUrl.
 */
public record Place(
        String id,
        Destination destination,
        PlaceType type,
        String name,
        String nameEn,
        String description,
        boolean curated,
        String osmId,
        List<PlaceTag> tags,
        long estCostVnd,
        long childCostVnd,
        PriceLevel priceLevel,
        String priceNote,
        String priceSourceUrl,
        LocalDate verifyAt,
        int durationMin,
        int childMinAge,
        boolean indoor,
        BestTime bestTime,
        String openingHours,
        double lat,
        double lon,
        double popularity,
        boolean dayTrip,
        String sourceUrl,
        List<String> estimatedFields,
        List<String> reviewFields,
        Map<String, String> fieldSources) {

    public Place {
        tags = List.copyOf(tags);
        estimatedFields = List.copyOf(estimatedFields);
        reviewFields = List.copyOf(reviewFields);
        fieldSources = Map.copyOf(fieldSources);
    }

    public boolean hasTag(PlaceTag tag) {
        return tags.contains(tag);
    }

    /** True when the field value is a category/editorial estimate; the UI shows "~". */
    public boolean isEstimated(String field) {
        return estimatedFields.contains(field);
    }

    public boolean isPriceEstimated() {
        return isEstimated("estCostVnd") || isEstimated("childCostVnd");
    }
}
