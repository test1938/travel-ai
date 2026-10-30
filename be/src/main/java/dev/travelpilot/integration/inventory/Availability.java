package dev.travelpilot.integration.inventory;

import java.time.LocalDate;

/**
 * Partner availability for one place, date and group size. {@code demo = true} means the numbers are simulated and
 * must be shown as such (no real partner is connected).
 */
public record Availability(String placeId, LocalDate date, int guests, boolean available, int remaining,
        String note, boolean demo) {}
