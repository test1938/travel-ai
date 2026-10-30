package dev.travelpilot.trip;

import dev.travelpilot.planner.Assembler;
import dev.travelpilot.planner.PlanValidator;
import dev.travelpilot.planner.PlannerFixtures;
import dev.travelpilot.planner.PlannerOrchestrator;
import dev.travelpilot.planner.RepairLoop;
import dev.travelpilot.trip.TripPlan.PlanMeta;
import java.util.List;
import java.util.Map;

/** A realistic assembled plan and orchestrator result for trip tests. */
final class TripFixtures {

    private TripFixtures() {}

    static PlannerOrchestrator.Result familyResult() {
        var ctx = PlannerFixtures.context();
        var draft = PlannerFixtures.validDraft();
        var report = new PlanValidator().validate(draft, ctx);
        var outcome = new RepairLoop.Outcome(draft, report, 0, false, null, true, "planner-v1", "stub", 1200, 300);
        TripPlan plan = new Assembler().assemble(draft, ctx, report.soft(), List.of(),
                new PlanMeta("planner-v1", "stub", false, 0, false, 1500));
        return new PlannerOrchestrator.Result(plan, outcome, Map.of("llm", 1400L));
    }
}
