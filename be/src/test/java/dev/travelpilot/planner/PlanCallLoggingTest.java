package dev.travelpilot.planner;

import static org.assertj.core.api.Assertions.assertThat;

import dev.travelpilot.common.ai.AiCallLog;
import dev.travelpilot.integration.weather.NoWeatherProvider;
import dev.travelpilot.support.Personas;
import dev.travelpilot.support.RecordedAiCalls;
import dev.travelpilot.support.StubChatModel;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/** T7.01: one ai_call_logs row per planner model call (first draft and each repair), none for the fallback. */
class PlanCallLoggingTest {

    private final StubChatModel model = new StubChatModel();
    private final RecordedAiCalls calls = new RecordedAiCalls();
    private final PlannerOrchestrator orchestrator =
            OfflinePlanning.orchestrator(model, OfflinePlanning.places(), new NoWeatherProvider(), calls.logger());

    private static final String VALID = JsonMapper.builder().build().writeValueAsString(PlannerFixtures.validDraft());
    private static final String INVALID = VALID.replace(PlannerFixtures.MY_KHE, Personas.CRAFTSMAN_BAR);

    @Test
    void validFirstDraftIsOneRow() {
        model.reply(VALID);

        orchestrator.plan(PlannerFixtures.family());

        assertThat(calls.rows()).singleElement().satisfies(row -> {
            assertThat(row.operation()).isEqualTo("PLAN");
            assertThat(row.promptVersion()).isEqualTo("planner-v1");
            assertThat(row.status()).isEqualTo(AiCallLog.Status.OK);
            assertThat(row.attempts()).isEqualTo(1);
            assertThat(row.toolCalls()).isEmpty();
        });
    }

    @Test
    void eachRepairAddsOneRow() {
        model.reply(INVALID, VALID);

        PlannerOrchestrator.Result result = orchestrator.plan(PlannerFixtures.family());

        assertThat(result.outcome().repairs()).isEqualTo(1);
        assertThat(calls.operations()).containsExactly("PLAN", "REPAIR");
        assertThat(calls.rows().get(1).promptVersion()).isEqualTo("repair-v1");
    }

    @Test
    void failedCallIsLoggedAndTheFallbackIsNot() {
        model.fail(new IllegalStateException("boom"));

        PlannerOrchestrator.Result result = orchestrator.plan(PlannerFixtures.family());

        assertThat(result.outcome().fallback()).isTrue();
        assertThat(calls.rows()).singleElement().satisfies(row -> {
            assertThat(row.status()).isEqualTo(AiCallLog.Status.ERROR);
            assertThat(row.errorCode()).isEqualTo("LLM_ERROR");
            assertThat(row.tokensIn()).isZero();
        });
    }
}
