package dev.travelpilot.integration.inventory;

import java.time.LocalDate;

/** Port for partner inventory (tickets, tables, rooms). Only the mock exists in the MVP. */
public interface PartnerInventoryProvider {

    Availability check(String placeId, LocalDate date, int guests);
}
