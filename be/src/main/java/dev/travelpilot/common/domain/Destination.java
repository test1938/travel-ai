package dev.travelpilot.common.domain;

import java.time.ZoneId;

/** The three supported destinations; centers match the dataset pipeline (enrichment/rules.py). */
public enum Destination {
    HANOI("Hà Nội", 21.0285, 105.8542),
    DANANG("Đà Nẵng", 16.0544, 108.2208),
    QUANGNINH("Quảng Ninh (Hạ Long)", 20.9590, 107.0450);

    /** All destinations are in Vietnam. */
    public static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final String displayName;
    private final double centerLat;
    private final double centerLon;

    Destination(String displayName, double centerLat, double centerLon) {
        this.displayName = displayName;
        this.centerLat = centerLat;
        this.centerLon = centerLon;
    }

    public String displayName() {
        return displayName;
    }

    public double centerLat() {
        return centerLat;
    }

    public double centerLon() {
        return centerLon;
    }
}
