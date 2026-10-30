package dev.travelpilot.recommendation;

import dev.travelpilot.catalog.Place;

/** A candidate with its score breakdown. */
public record ScoredPlace(Place place, ScoreBreakdown score) {

    public String id() {
        return place.id();
    }
}
