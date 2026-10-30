package dev.travelpilot.trip;

import dev.travelpilot.common.domain.Destination;
import dev.travelpilot.common.domain.PlaceTag;
import dev.travelpilot.common.error.ValidationFailedException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** Rules that need the clock or a whitelist; field ranges are Bean Validation annotations on TripRequest. */
@Component
public class TripRequestValidator {

    private final Clock clock;

    public TripRequestValidator(Clock clock) {
        this.clock = clock;
    }

    /** Throws {@link ValidationFailedException} (400 VALIDATION_FAILED) listing every problem. */
    public void validate(TripRequest req) {
        Map<String, String> errors = new LinkedHashMap<>();
        LocalDate today = LocalDate.now(clock.withZone(Destination.ZONE));
        if (req.startDate() != null && req.startDate().isBefore(today)) {
            errors.put("startDate", "Ngày đi phải từ hôm nay (" + today + ") trở đi.");
        }
        String notAllowed = req.preferences().stream()
                .filter(p -> !TripRequest.ALLOWED_PREFERENCES.contains(p))
                .map(PlaceTag::code)
                .sorted()
                .collect(Collectors.joining(", "));
        if (!notAllowed.isEmpty()) {
            errors.put("preferences", "Sở thích không hỗ trợ: " + notAllowed);
        }
        if (!errors.isEmpty()) {
            throw new ValidationFailedException(errors);
        }
    }
}
