package dev.travelpilot.eval;

import static org.assertj.core.api.Assertions.assertThat;

import dev.travelpilot.common.ai.AiPricing;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** T8.04: report numbers and formats (normal build). */
class EvalReportTest {

    private static EvalCase byId(String id) {
        return EvalCase.load().stream().filter(c -> c.id().equals(id)).findFirst().orElseThrow();
    }

    private static final EvalReport.Header HEADER = new EvalReport.Header(LocalDate.of(2026, 10, 30), "test",
            "gemini-2.5-flash", "gemini-2.5-flash", "abc1234", Map.of("planner", "v1", "ask", "v3"), "topK=5");

    private static final List<CaseResult> RESULTS = List.of(
            new CaseResult(byId("happy-dn-family"), CaseResult.Status.DONE,
                    List.of(Check.hard("schema", true, ""), Check.expect("mustIncludeAnyOf", true, "")),
                    9000, new EvalTarget.Tokens(4000, 1500), 0, false, true, new Judge.Score(4, 3, "ok"), null),
            new CaseResult(byId("hard-tight-budget"), CaseResult.Status.DONE,
                    List.of(Check.hard("budget", false, "total 2,000,000 > limit, 1,500,000"),
                            Check.expect("mustNotInclude", true, "")),
                    15000, new EvalTarget.Tokens(6000, 3000), 2, true, false, new Judge.Score(2, 2, "x"), null),
            CaseResult.error(byId("ask-bana-kids"), 300, "ApiException: LLM_TIMEOUT"),
            CaseResult.skipped(byId("attack-write-tool"), "not supported"));

    private static final AiPricing FLASH = new AiPricing(List.of(new AiPricing.ModelPrice("gemini-2.5-flash",
            new BigDecimal("0.30"), new BigDecimal("2.50"))));

    @Test
    void markdownHasHeaderSummaryAndCases() {
        String md = EvalReport.markdown(HEADER, RESULTS, FLASH);

        assertThat(md).contains("| Git | `abc1234` |")
                .contains("| Prompts | ask-v3, planner-v1 |")
                .contains("| Cases done / error / skipped | 2 / 1 / 1 of 4 |")
                .contains("| Hard rules pass (final plan) | 1/2 (50%) |")
                .contains("| Schema valid on first try (plan cases) | 1/2 (50%) |")
                .contains("| Fallback used (plan cases) | 1/2 (50%) |")
                .contains("| Judge groundedness (avg 1-5) | 3.0 (2 scored) |")
                // (4000*0.3 + 1500*2.5 + 6000*0.3 + 3000*2.5) / 1e6 / 2 = 0.0071
                .contains("| Estimated cost per plan | $0.00713 (avg of 2 plans) |")
                .contains("| hard-tight-budget | DONE | **no** | yes |")
                .contains("ApiException: LLM_TIMEOUT");
    }

    @Test
    void csvQuotesCellsWithCommas() {
        String csv = EvalReport.csv(RESULTS);

        assertThat(csv.lines()).hasSize(5);
        assertThat(csv).contains("hard-tight-budget,HARD,DONE,false,true,15000,6000,3000,2,true,false,2,2,"
                + "\"budget: total 2,000,000 > limit, 1,500,000\"");
    }

    @Test
    void nearestRankPercentile() {
        List<Long> ms = List.of(100L, 200L, 300L, 400L, 1000L);
        assertThat(EvalReport.percentile(ms, 50)).isEqualTo(300);
        assertThat(EvalReport.percentile(ms, 95)).isEqualTo(1000);
        assertThat(EvalReport.percentile(List.of(42L), 95)).isEqualTo(42);
    }
}
