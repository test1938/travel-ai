package dev.travelpilot.catalog;

import dev.travelpilot.common.domain.Destination;
import dev.travelpilot.common.domain.PlaceTag;
import dev.travelpilot.common.domain.PlaceType;
import dev.travelpilot.common.web.PageResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Browse the curated catalog. Tags use dataset codes: {@code ?tag=THEME-PARK}. */
@RestController
@RequestMapping("/api/v1/places")
public class PlaceController {

    private final PlaceService service;

    public PlaceController(PlaceService service) {
        this.service = service;
    }

    @GetMapping
    public PageResponse<Place> list(
            @RequestParam(required = false) Destination destination,
            @RequestParam(required = false) PlaceType type,
            @RequestParam(required = false) PlaceTag tag,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(PlaceQuery.MAX_SIZE) int size) {
        return service.find(new PlaceQuery(destination, type, tag, page, size));
    }

    @GetMapping("/{id}")
    public Place get(@PathVariable String id) {
        return service.get(id);
    }
}
