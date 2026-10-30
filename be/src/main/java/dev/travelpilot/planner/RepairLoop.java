package dev.travelpilot.planner;

import dev.travelpilot.common.config.TravelPilotProperties;
import dev.travelpilot.common.error.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * LLM draft -> validate -> repair with the exact hard violations (at most travelpilot.planner.max-repairs
 * times) -> greedy fallback if still invalid or if the model fails. Always returns a draft (ADR-010).
 */
@Component
public class RepairLoop {

    private static final Logger log = LoggerFactory.getLogger(RepairLoop.class);

    /** What happened while producing the final draft; feeds trip_versions and the eval metrics. */
    public record Outcome(
            TripPlanDraft draft,
            PlanValidator.Report report,
            int repairs,
            boolean fallback,
            String fallbackReason,
            boolean schemaValidFirstTry,
            String promptVersion,
            String model,
            int tokensIn,
            int tokensOut) {

        public boolean repaired() {
            return repairs > 0 && !fallback;
        }
    }

    private final PlannerClient llm;
    private final GreedyFallbackPlanner fallback;
    private final PlanValidator validator;
    private final int maxRepairs;

    public RepairLoop(PlannerClient llm, GreedyFallbackPlanner fallback, PlanValidator validator,
            TravelPilotProperties props) {
        this.llm = llm;
        this.fallback = fallback;
        this.validator = validator;
        this.maxRepairs = props.planner().maxRepairs();
    }

    public Outcome run(PlanContext ctx) {
        PlanAttempt attempt;
        try {
            attempt = llm.plan(ctx);
        } catch (ApiException e) {
            log.warn("Planner call failed ({}), using greedy fallback", e.code());
            return useFallback(ctx, e.code().name(), 0, false, null, 0, 0);
        }
        boolean firstTry = attempt.parsed();
        int tokensIn = attempt.tokensIn();
        int tokensOut = attempt.tokensOut();
        PlanValidator.Report report = validator.validate(attempt.draft(), ctx);
        String firstVersion = attempt.promptVersion();
        String promptVersion = firstVersion;

        int repairs = 0;
        while (!report.isValid() && repairs < maxRepairs) {
            try {
                attempt = llm.repair(ctx, attempt, report.hard());
            } catch (ApiException e) {
                log.warn("Repair call failed ({}), using greedy fallback", e.code());
                return useFallback(ctx, e.code().name(), repairs, firstTry, attempt.model(), tokensIn,
                        tokensOut);
            }
            repairs++;
            tokensIn += attempt.tokensIn();
            tokensOut += attempt.tokensOut();
            report = validator.validate(attempt.draft(), ctx);
            promptVersion = firstVersion + "+" + attempt.promptVersion();
        }
        if (report.isValid()) {
            log.info("Plan accepted after {} repair(s)", repairs);
            return new Outcome(attempt.draft(), report, repairs, false, null, firstTry, promptVersion,
                    attempt.model(), tokensIn, tokensOut);
        }
        log.warn("Plan still invalid after {} repair(s): {}", repairs,
                report.hard().stream().map(Violation::code).distinct().toList());
        return useFallback(ctx, "STILL_INVALID", repairs, firstTry, attempt.model(), tokensIn, tokensOut);
    }

    private Outcome useFallback(PlanContext ctx, String reason, int repairs, boolean firstTry, String model,
            int tokensIn, int tokensOut) {
        TripPlanDraft draft = fallback.plan(ctx).draft();
        return new Outcome(draft, validator.validate(draft, ctx), repairs, true, reason, firstTry,
                GreedyFallbackPlanner.VERSION, model, tokensIn, tokensOut);
    }
}
