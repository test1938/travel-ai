package dev.travelpilot.common.domain;

/** LOW < 100k (incl. free), MID < 400k, HIGH < 1M, PREMIUM otherwise (VND per adult). */
public enum PriceLevel {
    LOW,
    MID,
    HIGH,
    PREMIUM
}
