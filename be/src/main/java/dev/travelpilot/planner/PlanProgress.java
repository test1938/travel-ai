package dev.travelpilot.planner;

/**
 * Listener for the planning steps, so the UI can show real progress (T9.01). Steps, in order: weather,
 * candidates, retrieve, llm, assemble. Must be fast and must not throw.
 */
@FunctionalInterface
public interface PlanProgress {

    PlanProgress NONE = step -> {};

    void stepStarted(String step);
}
