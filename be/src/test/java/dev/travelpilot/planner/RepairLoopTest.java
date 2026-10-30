package dev.travelpilot.planner;

import static org.assertj.core.api.Assertions.assertThat;

import dev.travelpilot.common.ai.LlmGateway;
import dev.travelpilot.common.ai.PromptLoader;
import dev.travelpilot.common.config.TravelPilotProperties;
import dev.travelpilot.common.domain.Slot;
import dev.travelpilot.support.StubChatModel;
import dev.travelpilot.support.TestProps;
import java.time.Duration;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.ChatOptions;

class RepairLoopTest {

    private final StubChatModel model = new StubChatModel();
    private final PlanContext ctx = PlannerFixtures.context();

    private RepairLoop loop(int timeoutSeconds) {
        TravelPilotProperties d = TestProps.defaults();
        TravelPilotProperties props = new TravelPilotProperties(new TravelPilotProperties.Ai(timeoutSeconds),
                d.scoring(), d.planner(), d.rag(), d.ingest(), d.prompts());
        PlannerClient client = new PlannerClient(ChatClient.create(model),
                new LlmGateway(Executors.newVirtualThreadPerTaskExecutor(), props),
                new PlannerPrompts(new PromptLoader(props)), ChatOptions.builder().temperature(0.3).build());
        return new RepairLoop(client, new GreedyFallbackPlanner(), new PlanValidator(), props);
    }

    private static final String VALID = PlannerClientTest.json(PlannerFixtures.validDraft());
    private static final String UNKNOWN_ID = PlannerClientTest.json(
            PlannerFixtures.withDay(1, PlannerFixtures.item("danang-made-up", Slot.MORNING)));

    @Test
    void validFirstDraftIsAcceptedWithoutRepair() {
        model.reply(VALID);

        RepairLoop.Outcome o = loop(30).run(ctx);

        assertThat(o.draft()).isEqualTo(PlannerFixtures.validDraft());
        assertThat(o.repairs()).isZero();
        assertThat(o.fallback()).isFalse();
        assertThat(o.schemaValidFirstTry()).isTrue();
        assertThat(o.promptVersion()).isEqualTo("planner-v1");
        assertThat(model.prompts()).hasSize(1);
    }

    @Test
    void wrongIdIsRepairedOnTheSecondTry() {
        model.reply(UNKNOWN_ID, VALID);

        RepairLoop.Outcome o = loop(30).run(ctx);

        assertThat(o.repaired()).isTrue();
        assertThat(o.repairs()).isEqualTo(1);
        assertThat(o.fallback()).isFalse();
        assertThat(o.report().isValid()).isTrue();
        assertThat(o.promptVersion()).isEqualTo("planner-v1+repair-v1");
        assertThat(model.prompts().get(1).getContents()).contains("UNKNOWN_PLACE").contains("danang-made-up");
    }

    @Test
    void unparseableAnswerIsRepairedToo() {
        model.reply("not json", VALID);

        RepairLoop.Outcome o = loop(30).run(ctx);

        assertThat(o.schemaValidFirstTry()).isFalse();
        assertThat(o.repaired()).isTrue();
        assertThat(model.prompts().get(1).getContents()).contains("SCHEMA_INVALID").contains("not json");
    }

    @Test
    void threeWrongAnswersFallBackToGreedy() {
        model.reply(UNKNOWN_ID, UNKNOWN_ID, UNKNOWN_ID);

        RepairLoop.Outcome o = loop(30).run(ctx);

        assertThat(model.prompts()).hasSize(3);   // first try + 2 repairs
        assertThat(o.fallback()).isTrue();
        assertThat(o.fallbackReason()).isEqualTo("STILL_INVALID");
        assertThat(o.repairs()).isEqualTo(2);
        assertThat(o.repaired()).isFalse();
        assertThat(o.promptVersion()).isEqualTo(GreedyFallbackPlanner.VERSION);
        assertThat(o.report().isValid()).isTrue();
    }

    @Test
    void providerErrorGoesStraightToFallback() {
        model.fail(new RuntimeException("503 overloaded"));

        RepairLoop.Outcome o = loop(30).run(ctx);

        assertThat(o.fallback()).isTrue();
        assertThat(o.fallbackReason()).isEqualTo("LLM_ERROR");
        assertThat(o.report().isValid()).isTrue();
    }

    @Test
    void timeoutGoesToFallback() {
        model.replyAfter(Duration.ofSeconds(3), VALID);

        RepairLoop.Outcome o = loop(1).run(ctx);

        assertThat(o.fallback()).isTrue();
        assertThat(o.fallbackReason()).isEqualTo("LLM_TIMEOUT");
        assertThat(o.report().isValid()).isTrue();
    }
}
