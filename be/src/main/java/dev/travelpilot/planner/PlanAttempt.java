package dev.travelpilot.planner;

/**
 * Result of one planner call. {@code draft} is null when the model answer could not be parsed ({@code error}
 * says why); token counts are 0 for the greedy fallback.
 */
public record PlanAttempt(TripPlanDraft draft, String error, String promptVersion, String model, int tokensIn,
        int tokensOut, String rawJson) {

    public static PlanAttempt ofDraft(TripPlanDraft draft, String promptVersion, String model) {
        return new PlanAttempt(draft, null, promptVersion, model, 0, 0, null);
    }

    public boolean parsed() {
        return draft != null;
    }
}
