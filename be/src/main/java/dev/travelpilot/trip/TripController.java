package dev.travelpilot.trip;

import dev.travelpilot.security.Guest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Trips API. The owner is the guest of the bearer token; another guest's trip is 404 (not 403). */
@RestController
@RequestMapping("/api/v1/trips")
public class TripController {

    private final TripService service;

    public TripController(TripService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<TripPlan> create(
            Guest guest,
            @Valid @RequestBody TripRequest request) {
        TripPlan plan = service.create(guest.id(), request);
        return ResponseEntity.created(URI.create("/api/v1/trips/" + plan.tripId())).body(plan);
    }

    @GetMapping("/{id}")
    public TripPlan get(@PathVariable UUID id,
            Guest guest) {
        return service.get(id, guest.id());
    }

    @GetMapping("/{id}/versions/{version}")
    public TripPlan version(@PathVariable UUID id, @PathVariable @Min(1) int version,
            Guest guest) {
        return service.getVersion(id, version, guest.id());
    }
}
