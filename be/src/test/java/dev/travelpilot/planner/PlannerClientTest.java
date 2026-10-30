package dev.travelpilot.planner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.travelpilot.common.ai.LlmGateway;
import dev.travelpilot.common.ai.PromptLoader;
import dev.travelpilot.common.error.ApiException;
import dev.travelpilot.common.error.ErrorCode;
import dev.travelpilot.support.StubChatModel;
import dev.travelpilot.support.TestProps;
import java.util.List;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.ChatOptions;
import tools.jackson.databind.json.JsonMapper;

class PlannerClientTest {

    private final StubChatModel model = new StubChatModel();
    private final PlannerClient client = new PlannerClient(ChatClient.create(model),
            new LlmGateway(Executors.newVirtualThreadPerTaskExecutor(), TestProps.defaults()),
            new PlannerPrompts(new PromptLoader(TestProps.defaults())),
            ChatOptions.builder().temperature(0.3).build());

    static String json(TripPlanDraft draft) {
        return JsonMapper.builder().build().writeValueAsString(draft);
    }

    @Test
    void parsesAValidAnswer() {
        model.reply(json(PlannerFixtures.validDraft()));

        PlanAttempt attempt = client.plan(PlannerFixtures.context());

        assertThat(attempt.parsed()).isTrue();
        assertThat(attempt.draft()).isEqualTo(PlannerFixtures.validDraft());
        assertThat(attempt.promptVersion()).isEqualTo("planner-v1");
        String sent = model.prompts().getFirst().getContents();
        assertThat(sent).contains("<candidates>").contains("\"placeId\"");
        assertThat(model.prompts().getFirst().getOptions().getTemperature()).isEqualTo(0.3);
    }

    @Test
    void invalidJsonIsAnAttemptWithoutDraft() {
        model.reply("Xin lỗi, tôi không thể lập lịch.");

        PlanAttempt attempt = client.plan(PlannerFixtures.context());

        assertThat(attempt.parsed()).isFalse();
        assertThat(attempt.error()).isNotBlank();
        assertThat(attempt.rawJson()).isEqualTo("Xin lỗi, tôi không thể lập lịch.");
    }

    @Test
    void repairSendsThePreviousDraftAndProblems() {
        model.reply(json(PlannerFixtures.validDraft()), json(PlannerFixtures.validDraft()));
        PlanAttempt first = client.plan(PlannerFixtures.context());
        Violation problem = Violation.hard("UNKNOWN_PLACE", "Địa điểm không có trong danh sách", 1, "x");

        PlanAttempt repaired = client.repair(PlannerFixtures.context(), first, List.of(problem));

        assertThat(repaired.promptVersion()).isEqualTo("repair-v1");
        String sent = model.prompts().get(1).getContents();
        assertThat(sent).contains("<previous_draft>").contains("\"dayIndex\":1").contains("- UNKNOWN_PLACE");
    }

    @Test
    void providerErrorsPropagateAsApiException() {
        model.fail(new RuntimeException("503"));
        assertThatThrownBy(() -> client.plan(PlannerFixtures.context()))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).code()).isEqualTo(ErrorCode.LLM_ERROR));
    }
}
