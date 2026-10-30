package dev.travelpilot.eval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import dev.travelpilot.common.domain.Destination;
import dev.travelpilot.eval.RetrievalMetrics.CaseResult;
import dev.travelpilot.knowledge.KnowledgeChunk;
import dev.travelpilot.knowledge.KnowledgeLoader;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class RetrievalMetricsTest {

    private static GoldenCase golden(String id, Destination d, String... expected) {
        return new GoldenCase(id, d, "q-" + id, List.of(expected), null, null);
    }

    @Test
    void rankIsPositionOfFirstExpectedDoc() {
        GoldenCase g = golden("a", Destination.HANOI, "x", "y");
        assertThat(CaseResult.of(g, List.of("z", "y", "x"), null).rank()).isEqualTo(2);
        assertThat(CaseResult.of(g, List.of("z", "w"), null).rank()).isZero();
    }

    @Test
    void summaryComputesHitRatesAndMrr() {
        List<CaseResult> results = List.of(
                CaseResult.of(golden("a", Destination.HANOI, "x"), List.of("x"), true),              // rank 1
                CaseResult.of(golden("b", Destination.HANOI, "x"), List.of("1", "2", "x"), false),   // rank 3
                CaseResult.of(golden("c", Destination.DANANG, "x"), List.of("1", "2", "3", "4", "x"), null),   // 5
                CaseResult.of(golden("d", Destination.DANANG, "x"), List.of("1"), null));             // miss

        RetrievalMetrics.Summary s = RetrievalMetrics.summarize(results);

        assertThat(s.cases()).isEqualTo(4);
        assertThat(s.hit1()).isEqualTo(0.25);
        assertThat(s.hit3()).isEqualTo(0.50);
        assertThat(s.hit5()).isEqualTo(0.75);
        assertThat(s.mrr()).isCloseTo((1 + 1.0 / 3 + 1.0 / 5) / 4, within(1e-9));
        assertThat(s.mentionRate()).isEqualTo(0.5);
        assertThat(RetrievalMetrics.byDestination(results)).containsOnlyKeys(Destination.HANOI, Destination.DANANG);
    }

    @Test
    void goldenSetIsValidAgainstTheKnowledgeFiles() {
        List<GoldenCase> cases = GoldenCase.load();
        Set<String> docIds = new KnowledgeLoader().loadAll().stream()
                .map(KnowledgeChunk::docId).collect(Collectors.toSet());

        assertThat(cases).hasSize(24);
        assertThat(cases).allSatisfy(c -> assertThat(docIds).containsAll(c.expectedDocIds()));
    }

    @Test
    void reportHasSummaryAndPerCaseRows() {
        List<CaseResult> results = List.of(CaseResult.of(golden("hn-01", Destination.HANOI, "x"), List.of("x"), true));
        String md = RetrievalReport.markdown(LocalDate.of(2026, 10, 30), "topK=5", results);

        assertThat(md).contains("# Retrieval eval 2026-10-30")
                .contains("| HANOI | 1 | 100% | 100% | 100% | 1.00 | 100% |")
                .contains("| **ALL** |")
                .contains("| hn-01 | 1 | ok |");
    }
}
