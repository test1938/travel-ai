package dev.travelpilot.recommendation;

import dev.travelpilot.catalog.PlaceRepository;
import dev.travelpilot.common.config.TravelPilotProperties;
import dev.travelpilot.integration.weather.Forecast;
import dev.travelpilot.trip.TripRequest;
import org.springframework.stereotype.Service;

/** Loads the destination's catalog and runs the pure {@link CandidateSelector}. */
@Service
public class RecommendationService {

    private final PlaceRepository places;
    private final CandidateSelector selector;

    public RecommendationService(PlaceRepository places, TravelPilotProperties props) {
        this.places = places;
        this.selector = new CandidateSelector(props.scoring().weights());
    }

    public CandidateSet candidates(TripRequest req, Forecast forecast, HardFilter.Options options) {
        return selector.select(places.findByDestination(req.destination()), req, forecast, options);
    }

    public CandidateSet candidates(TripRequest req, Forecast forecast) {
        return candidates(req, forecast, HardFilter.Options.defaults());
    }
}
