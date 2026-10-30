package dev.travelpilot.eval;

import java.util.List;
import java.util.stream.Collectors;

/** Console summary of a run: one line per case and the pass counts. */
final class EvalRunSummary {

    private EvalRunSummary() {}

    static String text(List<CaseResult> results) {
        StringBuilder sb = new StringBuilder("\n=== Eval results ===\n");
        for (CaseResult r : results) {
            sb.append(String.format("%-24s %-7s %-7s hard=%-5s expect=%-5s %6d ms  %s%n", r.evalCase().id(),
                    r.evalCase().category(), r.status(), r.hardPass(), r.expectationsHit(), r.latencyMs(),
                    r.error() != null ? r.error()
                            : r.failed().stream().map(c -> c.name() + ": " + c.detail())
                                    .collect(Collectors.joining("; "))));
        }
        long done = results.stream().filter(CaseResult::done).count();
        sb.append(String.format("done %d/%d, hard pass %d, expectations hit %d%n", done, results.size(),
                results.stream().filter(CaseResult::hardPass).count(),
                results.stream().filter(CaseResult::expectationsHit).count()));
        return sb.toString();
    }
}
