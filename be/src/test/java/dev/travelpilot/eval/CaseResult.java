package dev.travelpilot.eval;

import java.util.List;

/**
 * Outcome of one eval case. {@code repairs}, {@code fallback} and {@code schemaFirstTry} describe the plan that was
 * checked (null when no plan was produced in this case, e.g. an answered question); {@code judge} may be null.
 */
public record CaseResult(
        EvalCase evalCase,
        Status status,
        List<Check> checks,
        long latencyMs,
        EvalTarget.Tokens tokens,
        Integer repairs,
        Boolean fallback,
        Boolean schemaFirstTry,
        Judge.Score judge,
        String error) {

    public enum Status { DONE, ERROR, SKIPPED }

    public CaseResult {
        checks = List.copyOf(checks);
    }

    static CaseResult skipped(EvalCase c, String why) {
        return new CaseResult(c, Status.SKIPPED, List.of(), 0, EvalTarget.Tokens.ZERO, null, null, null, null, why);
    }

    static CaseResult error(EvalCase c, long latencyMs, String error) {
        return new CaseResult(c, Status.ERROR, List.of(), latencyMs, EvalTarget.Tokens.ZERO, null, null, null, null,
                error);
    }

    public boolean done() {
        return status == Status.DONE;
    }

    /** All hard rules passed (only meaningful when {@link #done()}). */
    public boolean hardPass() {
        return done() && checks.stream().filter(Check::hard).allMatch(Check::passed);
    }

    /** All expectations passed. */
    public boolean expectationsHit() {
        return done() && checks.stream().filter(c -> !c.hard()).allMatch(Check::passed);
    }

    public List<Check> failed() {
        return checks.stream().filter(c -> !c.passed()).toList();
    }
}
