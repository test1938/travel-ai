package dev.travelpilot.recommendation;

import dev.travelpilot.integration.weather.WeatherProvider;
import dev.travelpilot.trip.TripRequest;
import dev.travelpilot.trip.TripRequestValidator;
import jakarta.validation.Valid;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Dev-only: shows ranked candidates with score breakdown and rejected places for a request. */
@Profile("dev")
@RestController
@RequestMapping("/api/v1/trips/candidates")
public class CandidatesController {

    private final RecommendationService recommendations;
    private final WeatherProvider weather;
    private final TripRequestValidator validator;

    public CandidatesController(RecommendationService recommendations, WeatherProvider weather,
            TripRequestValidator validator) {
        this.recommendations = recommendations;
        this.weather = weather;
        this.validator = validator;
    }

    @PostMapping
    public CandidateSet candidates(@Valid @RequestBody TripRequest request) {
        validator.validate(request);
        return recommendations.candidates(request, weather.forecast(request.destination(), request.dates()));
    }
}
