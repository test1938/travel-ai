package dev.travelpilot.trip;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Trips API. Until guest tokens (T6.01) the owner is identified by the {@code X-Guest-Id} header.
 */
@RestController
@RequestMapping("/api/v1/trips")
public class TripController {

    static final String GUEST_HEADER = "X-Guest-Id";
    static final String GUEST_PATTERN = "[A-Za-z0-9-]{1,64}";

    private final TripService service;

    public TripController(TripService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<TripPlan> create(
            @RequestHeader(value = GUEST_HEADER, defaultValue = "anonymous") @Pattern(regexp = GUEST_PATTERN) String guestId,
            @Valid @RequestBody TripRequest request) {
        TripPlan plan = service.create(guestId, request);
        return ResponseEntity.created(URI.create("/api/v1/trips/" + plan.tripId())).body(plan);
    }

    @GetMapping("/{id}")
    public TripPlan get(@PathVariable UUID id,
            @RequestHeader(value = GUEST_HEADER, defaultValue = "anonymous") @Pattern(regexp = GUEST_PATTERN) String guestId) {
        return service.get(id, guestId);
    }

    @GetMapping("/{id}/versions/{version}")
    public TripPlan version(@PathVariable UUID id, @PathVariable @Min(1) int version,
            @RequestHeader(value = GUEST_HEADER, defaultValue = "anonymous") @Pattern(regexp = GUEST_PATTERN) String guestId) {
        return service.getVersion(id, version, guestId);
    }
}
