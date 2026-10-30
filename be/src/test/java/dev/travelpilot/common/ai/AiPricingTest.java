package dev.travelpilot.common.ai;

import static org.assertj.core.api.Assertions.assertThat;

import dev.travelpilot.support.RecordedAiCalls;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

/** T7.05: cost per call from the configured price list. */
class AiPricingTest {

    static final AiPricing PRICES = new AiPricing(List.of(
            new AiPricing.ModelPrice("gemini-2.5-flash", new BigDecimal("0.30"), new BigDecimal("2.50")),
            new AiPricing.ModelPrice("gemini-2.5-flash-lite", new BigDecimal("0.10"), new BigDecimal("0.40"))));

    @Test
    void costIsTokensTimesPricePerMillion() {
        // 10,000 in * 0.30 + 2,000 out * 2.50 = 3,000 + 5,000 = 8,000 per million -> 0.008 USD
        assertThat(PRICES.cost("gemini-2.5-flash", 10_000, 2_000)).hasValueSatisfying(
                c -> assertThat(c).isEqualByComparingTo("0.008"));
        assertThat(PRICES.cost("gemini-2.5-flash", 0, 0)).hasValueSatisfying(
                c -> assertThat(c).isEqualByComparingTo("0"));
    }

    @Test
    void matchesVersionedNamesAndPrefersTheMostSpecificEntry() {
        assertThat(PRICES.priceOf("models/gemini-2.5-flash-001")).map(AiPricing.ModelPrice::model)
                .contains("gemini-2.5-flash");
        assertThat(PRICES.priceOf("gemini-2.5-flash-lite-preview")).map(AiPricing.ModelPrice::model)
                .contains("gemini-2.5-flash-lite");
        assertThat(PRICES.cost("gemini-2.5-pro", 1, 1)).isEmpty();
        assertThat(PRICES.cost(null, 1, 1)).isEmpty();                 // stub model: no cost
        assertThat(PRICES.priceOf("gemini-2.5-flashy")).isEmpty();       // prefix only up to a dash
    }

    @Test
    void eachRowCarriesItsCost() {
        RecordedAiCalls rows = new RecordedAiCalls();
        AiCallLogger calls = new AiCallLogger(rows, new SimpleMeterRegistry(), PRICES);

        calls.modelCall(AiCall.of("PLAN", "planner-v1"), new LlmUsage("gemini-2.5-flash", 10_000, 2_000), 9000, 1,
                AiCallLog.Status.OK, null);
        calls.modelCall(AiCall.of("PLAN", "planner-v1"), new LlmUsage(null, 0, 0), 10, 1,
                AiCallLog.Status.ERROR, "LLM_ERROR");

        assertThat(rows.rows().get(0).costUsd()).isEqualByComparingTo("0.008");
        assertThat(rows.rows().get(1).costUsd()).isNull();
    }
}
