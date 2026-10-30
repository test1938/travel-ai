package dev.travelpilot.recommendation;

/** Why a place was removed before scoring (shown by the dev candidates endpoint). */
public enum FilterReason {
    WRONG_DESTINATION,
    CHILD_TOO_YOUNG,
    TOO_EXPENSIVE,
    CLOSED_ON_TRIP_DATES,
    EXCLUDED_BY_USER,
    NOT_INDOOR
}
