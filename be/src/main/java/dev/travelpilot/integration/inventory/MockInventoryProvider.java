package dev.travelpilot.integration.inventory;

import java.time.LocalDate;
import org.springframework.stereotype.Component;

/**
 * Deterministic stand-in for a partner API: the remaining capacity depends only on {@code (placeId, date)}, so the
 * same question always gets the same answer (demos and tests are reproducible). Always {@code demo = true}.
 */
@Component
public class MockInventoryProvider implements PartnerInventoryProvider {

    static final int MAX_REMAINING = 40;
    static final String NOTE = "Dữ liệu mô phỏng (demo), chưa kết nối đối tác thật.";

    @Override
    public Availability check(String placeId, LocalDate date, int guests) {
        int remaining = remaining(placeId, date);
        return new Availability(placeId, date, guests, remaining >= guests, remaining, NOTE, true);
    }

    /**
     * 0..40 from a stable hash: String and LocalDate hash codes are specified by the JDK, so the value is the same
     * on every run and machine. About one date in seven is sold out.
     */
    static int remaining(String placeId, LocalDate date) {
        int h = mix((placeId + "|" + date).hashCode());
        int bucket = Math.floorMod(h, 7);
        return bucket == 0 ? 0 : Math.floorMod(h >>> 3, MAX_REMAINING) + 1;
    }

    /** Murmur3 finaliser: neighbouring dates have neighbouring hash codes, this spreads them out. */
    private static int mix(int h) {
        h ^= h >>> 16;
        h *= 0x85ebca6b;
        h ^= h >>> 13;
        h *= 0xc2b2ae35;
        return h ^ (h >>> 16);
    }
}
