package dev.travelpilot.planner;

/** Produces a draft for a planning context: the LLM planner or the greedy fallback (same contract). */
public interface DraftPlanner {

    PlanAttempt plan(PlanContext ctx);
}
