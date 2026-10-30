package dev.travelpilot.eval;

import dev.travelpilot.common.config.TravelPilotProperties;
import java.util.List;
import java.util.stream.Collectors;

/** Console summary of a run (one line per case and the pass counts) and the shared report header config. */
final class EvalRunSummary {

    private EvalRunSummary() {}

    /** The settings that change eval results, for the report header. */
    static String config(TravelPilotProperties props, boolean weather, boolean judge) {
        return "topK=%d, threshold=%.2f, maxRepairs=%d, retries=%d, chatTimeout=%ds, weather=%s, judge=%s"
                .formatted(props.rag().topK(), props.rag().threshold(), props.planner().maxRepairs(),
                props.ai().retry().maxRetries(), props.ai().chatTimeoutSeconds(), weather ? "on" : "off",
                judge ? "judge-" + props.prompts().getOrDefault("judge", "v1") : "off");
    }

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
