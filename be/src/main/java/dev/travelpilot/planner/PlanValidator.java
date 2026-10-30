package dev.travelpilot.planner;

import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/** Runs every {@link PlanRule}; a missing or unparseable draft is a single HARD SCHEMA_INVALID. */
@Component
public class PlanValidator {

    /** All violations of one draft. */
    public record Report(List<Violation> violations) {

        public Report {
            violations = List.copyOf(violations);
        }

        public List<Violation> hard() {
            return violations.stream().filter(Violation::isHard).toList();
        }

        public List<Violation> soft() {
            return violations.stream().filter(v -> !v.isHard()).toList();
        }

        public boolean isValid() {
            return hard().isEmpty();
        }
    }

    private final List<PlanRule> rules;

    public PlanValidator() {
        this(PlanRules.all());
    }

    PlanValidator(List<PlanRule> rules) {
        this.rules = List.copyOf(rules);
    }

    public Report validate(TripPlanDraft draft, PlanContext ctx) {
        if (draft == null) {
            return new Report(List.of(Violation.hard("SCHEMA_INVALID",
                    "Câu trả lời không phải JSON hợp lệ theo schema.", null, null)));
        }
        List<Violation> all = new ArrayList<>();
        rules.forEach(rule -> all.addAll(rule.check(draft, ctx)));
        return new Report(all);
    }
}
