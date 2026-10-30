package dev.travelpilot.common.domain;

/** Best part of the day to visit a place; ANY fits every slot. */
public enum BestTime {
    MORNING,
    AFTERNOON,
    EVENING,
    ANY;

    public boolean fits(Slot slot) {
        return this == ANY || name().equals(slot.name());
    }
}
