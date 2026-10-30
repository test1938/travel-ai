package dev.travelpilot.eval;

import dev.travelpilot.common.domain.Destination;
import dev.travelpilot.eval.RetrievalMetrics.CaseResult;
import dev.travelpilot.eval.RetrievalMetrics.Summary;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Markdown report for be/eval/results/. */
public final class RetrievalReport {

    private RetrievalReport() {}

    public static String markdown(LocalDate date, String config, List<CaseResult> results) {
        StringBuilder md = new StringBuilder();
        md.append("# Retrieval eval ").append(date).append('\n').append('\n');
        md.append("Config: ").append(config).append('\n').append('\n');
        md.append("| scope | cases | hit@1 | hit@3 | hit@5 | MRR | mustMention |\n");
        md.append("|---|---|---|---|---|---|---|\n");
        for (Map.Entry<Destination, Summary> e : RetrievalMetrics.byDestination(results).entrySet()) {
            row(md, e.getKey().name(), e.getValue());
        }
        row(md, "**ALL**", RetrievalMetrics.summarize(results));
        md.append('\n').append("| case | rank | mustMention | question | top retrieved |\n|---|---|---|---|---|\n");
        for (CaseResult r : results) {
            md.append("| ").append(r.golden().id())
                    .append(" | ").append(r.rank() == 0 ? "miss" : r.rank())
                    .append(" | ").append(r.mentionOk() == null ? "-" : r.mentionOk() ? "ok" : "missing")
                    .append(" | ").append(r.golden().question())
                    .append(" | ").append(r.retrievedDocIds().isEmpty() ? "-" : r.retrievedDocIds().getFirst())
                    .append(" |\n");
        }
        return md.toString();
    }

    private static void row(StringBuilder md, String scope, Summary s) {
        md.append("| ").append(scope)
                .append(" | ").append(s.cases())
                .append(" | ").append(pct(s.hit1()))
                .append(" | ").append(pct(s.hit3()))
                .append(" | ").append(pct(s.hit5()))
                .append(" | ").append(String.format(Locale.ROOT, "%.2f", s.mrr()))
                .append(" | ").append(s.mentionRate() == null ? "-" : pct(s.mentionRate()))
                .append(" |\n");
    }

    private static String pct(double v) {
        return String.format(Locale.ROOT, "%.0f%%", v * 100);
    }
}
