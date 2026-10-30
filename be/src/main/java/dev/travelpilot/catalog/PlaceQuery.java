package dev.travelpilot.catalog;

import dev.travelpilot.common.domain.Destination;
import dev.travelpilot.common.domain.PlaceTag;
import dev.travelpilot.common.domain.PlaceType;

/** Catalog filter; null fields mean "any". Page is 0-based. */
public record PlaceQuery(Destination destination, PlaceType type, PlaceTag tag, int page, int size) {

    public static final int MAX_SIZE = 100;

    public PlaceQuery {
        if (page < 0) {
            throw new IllegalArgumentException("page must be >= 0");
        }
        if (size < 1 || size > MAX_SIZE) {
            throw new IllegalArgumentException("size must be between 1 and " + MAX_SIZE);
        }
    }

    public int offset() {
        return page * size;
    }
}
