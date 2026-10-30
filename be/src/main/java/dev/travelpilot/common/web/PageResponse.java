package dev.travelpilot.common.web;

import java.util.List;

/** One page of results; {@code page} is 0-based. */
public record PageResponse<T>(List<T> items, int page, int size, long total) {

    public PageResponse {
        items = List.copyOf(items);
    }
}
