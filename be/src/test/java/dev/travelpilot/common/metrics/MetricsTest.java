package dev.travelpilot.common.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import dev.travelpilot.common.ai.AiCall;
import dev.travelpilot.common.ai.AiCallLog;
import dev.travelpilot.common.ai.AiCallLogger;
import dev.travelpilot.common.ai.LlmUsage;
import dev.travelpilot.common.config.TravelPilotProperties;
import dev.travelpilot.common.error.ApiException;
import dev.travelpilot.integration.weather.NoWeatherProvider;
import dev.travelpilot.planner.OfflinePlanning;
import dev.travelpilot.planner.PlannerFixtures;
import dev.travelpilot.planner.PlannerOrchestrator;
import dev.travelpilot.security.RateLimitInterceptor;
import dev.travelpilot.security.RateLimiter;
import dev.travelpilot.support.StubChatModel;
import io.micrometer.core.instrument.Statistic;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.micrometer.metrics.actuate.endpoint.MetricsEndpoint;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.json.JsonMapper;

/** T7.03: the tp.* metrics, read back through the same MetricsEndpoint that serves /actuator/metrics. */
class MetricsTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final MetricsEndpoint endpoint = new MetricsEndpoint(registry);

    private double value(String name, Statistic statistic, String... tags) {
        return endpoint.metric(name, List.of(tags)).getMeasurements().stream()
                .filter(s -> s.getStatistic() == statistic).findFirst().orElseThrow().getValue();
    }

    private List<String> tagValues(String name, String tag) {
        return endpoint.metric(name, null).getAvailableTags().stream()
                .filter(t -> t.getTag().equals(tag)).findFirst().orElseThrow().getValues().stream().sorted().toList();
    }

    @Test
    void onePlanFillsPlanLatencyPerStep() {
        StubChatModel model = new StubChatModel();
        model.reply(JsonMapper.builder().build().writeValueAsString(PlannerFixtures.validDraft()));
        PlannerOrchestrator orchestrator = OfflinePlanning.orchestrator(model, OfflinePlanning.places(),
                new NoWeatherProvider(), AiCallLogger.none(), registry);

        orchestrator.plan(PlannerFixtures.family());

        assertThat(tagValues(TpMetrics.PLAN_LATENCY, "step"))
                .containsExactly("assemble", "candidates", "llm", "retrieve", "total", "weather");
        assertThat(value(TpMetrics.PLAN_LATENCY, Statistic.COUNT, "step:total")).isEqualTo(1.0);
        assertThat(value(TpMetrics.PLAN_REPAIRS, Statistic.TOTAL)).isZero();
        assertThat(endpoint.metric(TpMetrics.PLAN_FALLBACK, null)).isNull();
    }

    @Test
    void fallbackIsCountedWithItsReason() {
        StubChatModel model = new StubChatModel();
        model.fail(new IllegalStateException("down"));
        OfflinePlanning.orchestrator(model, OfflinePlanning.places(), new NoWeatherProvider(), AiCallLogger.none(),
                registry).plan(PlannerFixtures.family());

        assertThat(value(TpMetrics.PLAN_FALLBACK, Statistic.COUNT, "reason:LLM_ERROR")).isEqualTo(1.0);
    }

    @Test
    void modelCallsCountTokensAndToolNamesStayBounded() {
        AiCallLogger calls = new AiCallLogger(row -> {}, registry);

        calls.modelCall(AiCall.of("ASK", "ask-v3"), new LlmUsage("gemini-2.5-flash", 1200, 300), 900, 1,
                AiCallLog.Status.OK, null);
        calls.modelCall(AiCall.of("ASK", "ask-v3"), new LlmUsage("gemini-2.5-flash", 800, 200), 700, 1,
                AiCallLog.Status.OK, null);
        calls.toolCall("getWeatherForecast", true, true, 120);
        calls.toolCall("deleteTrip", false, false, 1);
        calls.toolCall("x".repeat(500), false, false, 1);

        assertThat(value(TpMetrics.LLM_TOKENS, Statistic.COUNT, "operation:ASK", "type:input")).isEqualTo(2000.0);
        assertThat(value(TpMetrics.LLM_TOKENS, Statistic.COUNT, "type:output")).isEqualTo(500.0);
        assertThat(value(TpMetrics.LLM_LATENCY, Statistic.COUNT, "status:OK")).isEqualTo(2.0);
        assertThat(tagValues(TpMetrics.TOOL_LATENCY, "tool")).containsExactly("getWeatherForecast", "unknown");
        assertThat(value(TpMetrics.TOOL_LATENCY, Statistic.COUNT, "tool:unknown", "status:REJECTED"))
                .isEqualTo(2.0);
    }

    @Test
    void rateLimitRejectionsAreCountedByKind() throws Exception {
        RateLimitInterceptor interceptor = new RateLimitInterceptor(
                new RateLimiter(new TravelPilotProperties.RateLimit(true, 1, 1, 1, 100)), registry);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/ask");

        assertThat(interceptor.preHandle(request, new MockHttpServletResponse(), new Object())).isTrue();
        try {
            interceptor.preHandle(request, new MockHttpServletResponse(), new Object());
        } catch (ApiException expected) {
            // 429, counted below
        }

        assertThat(value(TpMetrics.RATELIMIT_REJECTED, Statistic.COUNT, "kind:MESSAGE")).isEqualTo(1.0);
    }
}
