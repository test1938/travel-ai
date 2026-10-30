package dev.travelpilot.planner;

import java.util.List;

/** One validation rule (Strategy); new rules are added without touching {@link PlanValidator}. */
@FunctionalInterface
public interface PlanRule {

    List<Violation> check(TripPlanDraft draft, PlanContext ctx);
}
