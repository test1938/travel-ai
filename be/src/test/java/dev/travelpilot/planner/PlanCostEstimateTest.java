package dev.travelpilot.planner;

import static org.assertj.core.api.Assertions.assertThat;

import dev.travelpilot.common.ai.AiPricing;
import dev.travelpilot.support.StubChatModel;
import dev.travelpilot.support.TestProps;
import dev.travelpilot.trip.TripRequest;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.json.JsonMapper;

/**
 * T7.05 offline estimate of the model cost of one itinerary, from the real planner prompt (captured by the stub
 * model) and Gemini 2.5 Flash list prices. Tokens are estimated as characters / 3, which is on the high side for
 * Vietnamese text; the measured value comes from ai_call_logs (GET /internal/ai-costs in dev). Fails when the
 * prompt grows so much that one plan would cost more than the budget below.
 */
class PlanCostEstimateTest {

    private static final Logger log = LoggerFactory.getLogger(PlanCostEstimateTest.class);

    static final double CHARS_PER_TOKEN = 3.0;
    /** Upper bound per plan in USD: first draft plus two repairs (the worst case before the fallback). */
    static final BigDecimal WORST_CASE_BUDGET_USD = new BigDecimal("0.02");

    private static final AiPricing FLASH = new AiPricing(List.of(new AiPricing.ModelPrice("gemini-2.5-flash",
            new BigDecimal("0.30"), new BigDecimal("2.50"))));

    @Test
    void onePlanCostsLessThanTwoCentsEvenWithTwoRepairs() {
        StubChatModel model = new StubChatModel();
        String draft = JsonMapper.builder().build().writeValueAsString(PlannerFixtures.validDraft());
        model.reply(draft);
        TripRequest family = PlannerFixtures.family();
        OfflinePlanning.orchestrator(model, OfflinePlanning.places()).plan(family);

        int promptTokens = tokens(model.prompts().getFirst().getContents());
        int draftTokens = tokens(draft) * family.days() / Math.max(1, PlannerFixtures.validDraft().days().size());
        int outputTokens = draftTokens + TestProps.defaults().ai().thinkingBudget();   // thinking bills as output
        BigDecimal oneCall = FLASH.cost("gemini-2.5-flash", promptTokens, outputTokens).orElseThrow();
        // a repair sends the planner prompt again plus the previous draft and the violations
        BigDecimal repair = FLASH.cost("gemini-2.5-flash", promptTokens + draftTokens + 200, outputTokens)
                .orElseThrow();
        BigDecimal worst = oneCall.add(repair).add(repair);

        log.info("Plan cost estimate for {} days: prompt ~{} tokens, output ~{} tokens, 1 call ${}, "
                + "with 2 repairs ${}", family.days(), promptTokens, outputTokens, oneCall, worst);
        assertThat(promptTokens).isPositive();
        assertThat(worst).isLessThan(WORST_CASE_BUDGET_USD);
    }

    private static int tokens(String text) {
        return (int) Math.ceil(text.length() / CHARS_PER_TOKEN);
    }
}
