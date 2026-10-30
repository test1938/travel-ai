package dev.travelpilot.eval;

import static org.assertj.core.api.Assertions.assertThat;

import dev.travelpilot.catalog.Place;
import dev.travelpilot.planner.OfflinePlanning;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * The same cases without any model ({@link OfflineEvalTarget}): a baseline for the deterministic safety net.
 * Run: {@code ./mvnw test -Dgroups=eval -Dtest=OfflineEvalRunner}. No Docker or key needed.
 */
@Tag("eval")
class OfflineEvalRunner {

    @Test
    void cases() {
        Map<String, Place> catalog = OfflinePlanning.catalog().stream()
                .collect(Collectors.toMap(Place::id, Function.identity()));

        List<CaseResult> results = new EvalEngine(catalog, LocalDate.now(), null)
                .run(EvalCase.load(), new OfflineEvalTarget());

        System.out.println(EvalRunSummary.text(results));
        assertThat(results).hasSize(20);
    }
}
