package dev.travelpilot.planner;

import dev.travelpilot.knowledge.Evidence;
import dev.travelpilot.knowledge.Source;

/** Evidence passage shown to the planner under a stable id such as "ev-3". */
public record PlanEvidence(String evId, Evidence evidence) {

    public Source toSource() {
        return Source.of(evId, evidence);
    }
}
