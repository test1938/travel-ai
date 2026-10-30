package dev.travelpilot.conversation;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import dev.travelpilot.common.ai.AiCallLog;
import dev.travelpilot.planner.OfflinePlanning;
import dev.travelpilot.support.Personas;
import dev.travelpilot.support.RecordedAiCalls;
import dev.travelpilot.support.StubChatModel;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

/** T5.04: the model picks calls as JSON, the backend validates and runs them, at most 3 rounds. */
class ToolLoopTest {

    private final StubChatModel model = new StubChatModel();
    private final RecordedAiCalls calls = new RecordedAiCalls();
    private final ToolLoop loop = ConversationFixtures.toolLoop(model, OfflinePlanning.places(),
            ConversationFixtures.rainEveryDay(), calls.logger());
    private final ListAppender<ILoggingEvent> logs = new ListAppender<>();

    @BeforeEach
    void captureLogs() {
        logs.start();
        ((Logger) LoggerFactory.getLogger(ToolLoop.class)).addAppender(logs);
    }

    @AfterEach
    void releaseLogs() {
        ((Logger) LoggerFactory.getLogger(ToolLoop.class)).detachAppender(logs);
    }

    private static final String TOMORROW = ConversationFixtures.CLOCK.instant()
            .atZone(ConversationFixtures.CLOCK.getZone()).toLocalDate().plusDays(1).toString();

    private static String weatherCall(int days, boolean done) {
        return """
                {"calls":[{"name":"getWeatherForecast","arguments":{"destination":"DANANG","from":"%s","days":%d}}],
                 "done":%s}""".formatted(TOMORROW, days, done);
    }

    private List<ToolLoop.ToolResult> run(String message) {
        return loop.run(ConversationFixtures.context(), message);
    }

    @Test
    void weatherQuestionCallsTheForecastToolAndLogsIt() {
        model.reply(weatherCall(1, true));

        List<ToolLoop.ToolResult> results = run("Mai Đà Nẵng trời thế nào?");

        assertThat(results).singleElement().satisfies(r -> {
            assertThat(r.ok()).isTrue();
            assertThat(r.name()).isEqualTo("getWeatherForecast");
            assertThat(r.output()).contains(TOMORROW).contains("Mưa (80% mưa)").contains("23–27°C");
            assertThat(r.toEvidence().title()).isEqualTo("Dự báo thời tiết Open-Meteo");
        });
        assertThat(model.prompts()).hasSize(1);
        String system = model.prompts().getFirst().getInstructions().getFirst().getText();
        assertThat(system).contains("getWeatherForecast", "searchPlaces", "checkAvailability")
                .contains("\"days\"")                                   // JSON schema from @ToolParam
                .contains("Today is " + ConversationFixtures.CLOCK.instant()
                        .atZone(ConversationFixtures.CLOCK.getZone()).toLocalDate());
        assertThat(logs.list).extracting(ILoggingEvent::getFormattedMessage)
                .anyMatch(m -> m.startsWith("tool_call name=getWeatherForecast")
                        && m.contains("destination=DANANG") && m.contains("status=OK"));
    }

    @Test
    void eachModelCallAndToolExecutionIsOneAiCallLogRow() {
        model.reply(weatherCall(30, false), weatherCall(2, true));

        run("Thời tiết cả tháng tới thế nào?");

        assertThat(calls.operations()).containsExactly("TOOLS", "TOOL", "TOOLS", "TOOL");
        assertThat(calls.rows().get(0).promptVersion()).isEqualTo("tools-v1");
        assertThat(calls.rows().get(1).toolCalls()).containsExactly("getWeatherForecast");
        assertThat(calls.rows().get(1).status()).isEqualTo(AiCallLog.Status.REJECTED);
        assertThat(calls.rows().get(3).status()).isEqualTo(AiCallLog.Status.OK);
    }

    @Test
    void invalidArgumentsAreRejectedAndFedBackForAFix() {
        model.reply(weatherCall(30, false), weatherCall(2, true));

        List<ToolLoop.ToolResult> results = run("Thời tiết cả tháng tới thế nào?");

        assertThat(results).extracting(ToolLoop.ToolResult::status)
                .containsExactly(ToolLoop.Status.REJECTED, ToolLoop.Status.OK);
        assertThat(results.getFirst().output()).contains("days must be between 1 and 7");
        assertThat(model.prompts().get(1).getContents()).contains("REJECTED: days must be between 1 and 7");
        assertThat(logs.list).extracting(ILoggingEvent::getFormattedMessage)
                .anyMatch(m -> m.contains("status=REJECTED"));
    }

    @Test
    void rejectsUnknownToolsPlacesDestinationsAndTags() {
        assertThat(loop.execute(new ToolLoop.Call("deleteTrip", java.util.Map.of())).output())
                .contains("Unknown tool");
        assertThat(loop.execute(new ToolLoop.Call("checkAvailability",
                java.util.Map.of("placeId", "made-up-id", "date", TOMORROW, "guests", 3))).output())
                .contains("Unknown placeId");
        assertThat(loop.execute(new ToolLoop.Call("checkAvailability",
                java.util.Map.of("placeId", Personas.BA_NA, "date", TOMORROW, "guests", 99))).output())
                .contains("guests must be between 1 and 20");
        assertThat(loop.execute(new ToolLoop.Call("getWeatherForecast",
                java.util.Map.of("destination", "PARIS", "from", TOMORROW, "days", 1))).output())
                .contains("Unknown destination");
        assertThat(loop.execute(new ToolLoop.Call("searchPlaces",
                java.util.Map.of("destination", "DANANG", "tags", List.of("CASINO")))).output())
                .contains("Unknown tag");
    }

    @Test
    void validSearchAndAvailabilityCalls() {
        ToolLoop.ToolResult search = loop.execute(new ToolLoop.Call("searchPlaces",
                java.util.Map.of("destination", "DANANG", "indoor", true, "maxChildAge", 6)));
        assertThat(search.ok()).isTrue();
        assertThat(search.output()).contains("trong nhà").doesNotContain("ngoài trời");

        ToolLoop.ToolResult availability = loop.execute(new ToolLoop.Call("checkAvailability",
                java.util.Map.of("placeId", Personas.BA_NA, "date", TOMORROW, "guests", 3)));
        assertThat(availability.ok()).isTrue();
        assertThat(availability.output()).contains("Bà Nà").contains("mô phỏng");
    }

    @Test
    void stopsAfterThreeRoundsAndThreeCallsPerRound() {
        String fiveCalls = """
                {"calls":[%1$s,%1$s,%1$s,%1$s,%1$s],"done":false}""".formatted("""
                {"name":"getWeatherForecast","arguments":{"destination":"DANANG","from":"%s","days":1}}"""
                .formatted(TOMORROW));
        model.reply(fiveCalls, fiveCalls, fiveCalls, fiveCalls);

        List<ToolLoop.ToolResult> results = run("Mấy ngày tới trời có mưa không?");

        assertThat(model.prompts()).hasSize(ToolLoop.MAX_ROUNDS);
        assertThat(results).hasSize(ToolLoop.MAX_ROUNDS * ToolLoop.MAX_CALLS_PER_ROUND);
    }

    @Test
    void ordinaryQuestionsSkipToolsWithoutAModelCall() {
        assertThat(run("Bà Nà có hợp với bé 6 tuổi không?")).isEmpty();
        assertThat(run("Vì sao Đà Nẵng có Bảo tàng Chăm trong lịch?")).isEmpty();
        assertThat(model.prompts()).isEmpty();
    }

    @Test
    void modelFailureEndsTheLoopQuietly() {
        model.fail(new RuntimeException("quota"));
        assertThat(run("Mai trời có mưa không?")).isEmpty();

        model.reply("not json");
        assertThat(run("Mai trời có mưa không?")).isEmpty();
    }

    @Test
    void longArgumentValuesAreCutInLogs() {
        assertThat(ToolLoop.masked(java.util.Map.of("q", "x".repeat(100))).get("q").toString())
                .hasSize(ToolLoop.MAX_LOGGED_VALUE + 1);
    }
}
