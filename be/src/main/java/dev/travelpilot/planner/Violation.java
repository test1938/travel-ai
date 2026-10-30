package dev.travelpilot.planner;

/**
 * A broken planning rule. HARD violations must be repaired (or trigger the fallback); SOFT ones become
 * warnings on the plan. dayIndex and placeId are optional.
 */
public record Violation(String code, Severity severity, String message, Integer dayIndex, String placeId) {

    public enum Severity { HARD, SOFT }

    public static Violation hard(String code, String message, Integer dayIndex, String placeId) {
        return new Violation(code, Severity.HARD, message, dayIndex, placeId);
    }

    public static Violation soft(String code, String message, Integer dayIndex, String placeId) {
        return new Violation(code, Severity.SOFT, message, dayIndex, placeId);
    }

    public boolean isHard() {
        return severity == Severity.HARD;
    }
}
