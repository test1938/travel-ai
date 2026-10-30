package dev.travelpilot.recommendation;

import dev.travelpilot.catalog.Place;
import dev.travelpilot.integration.weather.Forecast;
import dev.travelpilot.trip.TripRequest;

/** One weighted part of a place's score; pure, returns a value in [0, 1]. */
public interface ScoreComponent {

    /** Everything a component may look at besides the place itself. */
    record Context(TripRequest request, Forecast forecast) {}

    double score(Place place, Context ctx);

    static double clamp(double v) {
        return Math.max(0, Math.min(1, v));
    }
}
