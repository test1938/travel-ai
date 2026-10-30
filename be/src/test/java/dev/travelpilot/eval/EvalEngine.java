package dev.travelpilot.eval;

import dev.travelpilot.catalog.Place;
import dev.travelpilot.conversation.ChatService;
import dev.travelpilot.planner.PlannerOrchestrator;
import dev.travelpilot.planner.RepairLoop;
import dev.travelpilot.trip.TripPlan;
import dev.travelpilot.trip.TripRequest;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Runs the cases against a target: HAPPY/HARD through the planner orchestrator, MODIFY/ASK/ATTACK as a saved trip
 * plus one chat message. One failing case never stops the run; its error is reported.
 */
public class EvalEngine {

    private static final Logger log = LoggerFactory.getLogger(EvalEngine.class);

    private final Map<String, Place> catalog;
    private final LocalDate today;
    private final Judge judge;

    /** {@code judge} may be null (no LLM-as-judge scores). */
    public EvalEngine(Map<String, Place> catalog, LocalDate today, Judge judge) {
        this.catalog = catalog;
        this.today = today;
        this.judge = judge;
    }

    public List<CaseResult> run(List<EvalCase> cases, EvalTarget target) {
        List<CaseResult> results = new ArrayList<>();
        for (EvalCase c : cases) {
            CaseResult r = runOne(c, target);
            log.info("eval {} {}: {} hard={} expect={} {} ms{}", c.id(), r.status(),
                    r.failed().stream().map(Check::name).toList(), r.hardPass(), r.expectationsHit(),
                    r.latencyMs(), r.error() == null ? "" : " error=" + r.error());
            results.add(r);
        }
        return results;
    }

    CaseResult runOne(EvalCase c, EvalTarget target) {
        if (!target.supports(c.category())) {
            return CaseResult.skipped(c, "not supported by " + target.mode());
        }
        TripRequest req = c.request().toRequest(today);
        long start = System.currentTimeMillis();
        try {
            return c.category().chats() ? chatCase(c, req, target) : planCase(c, req, target);
        } catch (RuntimeException e) {
            return CaseResult.error(c, System.currentTimeMillis() - start,
                    e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private CaseResult planCase(EvalCase c, TripRequest req, EvalTarget target) {
        EvalTarget.Tokens before = target.tokens();
        long start = System.currentTimeMillis();
        PlannerOrchestrator.Result result = target.plan(req);
        long latency = System.currentTimeMillis() - start;
        EvalTarget.Tokens used = target.tokens().minus(before);

        List<Check> checks = new ArrayList<>(EvalChecks.plan(c, req, result.plan(), catalog));
        checks.addAll(EvalChecks.cost(c, latency, used.total()));
        RepairLoop.Outcome o = result.outcome();
        Judge.Score score = judge == null ? null : judge.plan(req, result.plan());
        return new CaseResult(c, CaseResult.Status.DONE, checks, latency, used, o.repairs(), o.fallback(),
                o.schemaValidFirstTry(), score, null);
    }

    private CaseResult chatCase(EvalCase c, TripRequest req, EvalTarget target) {
        String guest = "eval-" + c.id() + "-" + UUID.randomUUID();
        TripPlan v1 = target.createTrip(guest, req);

        EvalTarget.Tokens before = target.tokens();
        long start = System.currentTimeMillis();
        ChatService.ChatReply reply = target.chat(v1.tripId(), guest, c.message());
        long latency = System.currentTimeMillis() - start;
        EvalTarget.Tokens used = target.tokens().minus(before);

        TripPlan after = target.current(v1.tripId(), guest);
        List<Check> checks = new ArrayList<>(EvalChecks.chat(c, reply, v1, after));
        // the final plan must still satisfy every hard rule (attacks and changes included)
        checks.addAll(EvalChecks.plan(c, req, after, catalog));
        checks.addAll(EvalChecks.cost(c, latency, used.total()));

        boolean changed = after.version() != v1.version();
        Judge.Score score = null;
        if (judge != null) {
            score = c.category() == EvalCase.Category.ASK ? judge.answer(c.message(), reply)
                    : changed ? judge.plan(req, after) : null;
        }
        return new CaseResult(c, CaseResult.Status.DONE, checks, latency, used,
                changed ? after.meta().repairs() : null, changed ? after.meta().fallback() : null, null, score,
                null);
    }
}
