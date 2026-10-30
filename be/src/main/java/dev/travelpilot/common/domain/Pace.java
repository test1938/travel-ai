package dev.travelpilot.common.domain;

/** Trip pace; limits ACTIVITY items per day (meals are not counted). */
public enum Pace {
    RELAXED(2),
    NORMAL(3),
    PACKED(4);

    private final int maxActivitiesPerDay;

    Pace(int maxActivitiesPerDay) {
        this.maxActivitiesPerDay = maxActivitiesPerDay;
    }

    public int maxActivitiesPerDay() {
        return maxActivitiesPerDay;
    }
}
