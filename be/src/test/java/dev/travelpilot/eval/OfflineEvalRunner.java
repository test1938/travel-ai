package dev.travelpilot.eval;

import static org.assertj.core.api.Assertions.assertThat;

import dev.travelpilot.catalog.Place;
import dev.travelpilot.common.ai.AiPricing;
import dev.travelpilot.planner.OfflinePlanning;
import dev.travelpilot.support.TestProps;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * The same cases without any model ({@link OfflineEvalTarget}): a baseline for the deterministic safety net.
 * Run: {@code ./mvnw test -Dgroups=eval -Dtest=OfflineEvalRunner [-Deval.label=...]}. No Docker or key needed.
 * Writes be/eval/results/YYYY-MM-DD-&lt;label&gt;.md and .csv.
 */
@Tag("eval")
class OfflineEvalRunner {

    @Test
    void cases() {
        Map<String, Place> catalog = OfflinePlanning.catalog().stream()
                .collect(Collectors.toMap(Place::id, Function.identity()));

        OfflineEvalTarget target = new OfflineEvalTarget();
        List<CaseResult> results = new EvalEngine(catalog, LocalDate.now(), null).run(EvalCase.load(), target);

        System.out.println(EvalRunSummary.text(results));
        LocalDate today = LocalDate.now();
        EvalReport.Written written = EvalReport.write(new EvalReport.Header(today,
                System.getProperty("eval.label", "offline-baseline"), target.mode(), target.model(),
                EvalReport.gitSha(), TestProps.defaults().prompts(),
                EvalRunSummary.config(TestProps.defaults(), false, false)), results, AiPricing.none());
        System.out.println("Report written to " + written.markdown().toAbsolutePath());
        assertThat(results).hasSize(20);
    }
}
