package dev.travelpilot.catalog;

import dev.travelpilot.common.error.ApiException;
import dev.travelpilot.common.error.ErrorCode;
import dev.travelpilot.common.web.PageResponse;
import org.springframework.stereotype.Service;

/** Read-only access to the curated catalog for the API. */
@Service
public class PlaceService {

    private final PlaceRepository repository;

    public PlaceService(PlaceRepository repository) {
        this.repository = repository;
    }

    public PageResponse<Place> find(PlaceQuery query) {
        return new PageResponse<>(repository.find(query), query.page(), query.size(), repository.count(query));
    }

    public Place get(String id) {
        return repository.findById(id).orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
    }
}
