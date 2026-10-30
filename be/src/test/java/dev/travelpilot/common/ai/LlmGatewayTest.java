package dev.travelpilot.common.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.travelpilot.common.config.TravelPilotProperties;
import dev.travelpilot.common.error.ApiException;
import dev.travelpilot.common.error.ErrorCode;
import dev.travelpilot.common.metrics.TpMetrics;
import dev.travelpilot.support.RecordedAiCalls;
import dev.travelpilot.support.StubChatModel;
import dev.travelpilot.support.TestProps;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;

/** T7.04: retries only 429, 5xx and timeouts, at most twice, with jittered exponential backoff. */
class LlmGatewayTest {

    private final StubChatModel model = new StubChatModel();
    private final RecordedAiCalls rows = new RecordedAiCalls();
    private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
    private final List<Duration> waits = new ArrayList<>();

    private LlmGateway gateway(int timeoutSeconds) {
        TravelPilotProperties d = TestProps.defaults();
        TravelPilotProperties props = new TravelPilotProperties(
                new TravelPilotProperties.Ai(timeoutSeconds, 2048, 8192, 1024,
                        new TravelPilotProperties.Retry(2, 500, 4000)),
                d.scoring(), d.planner(), d.rag(), d.ingest(), d.weather(), d.security(), d.prompts());
        return new LlmGateway(Executors.newVirtualThreadPerTaskExecutor(), props,
                new AiCallLogger(rows, meters), waits::add, new Random(42));
    }

    private ChatResponse call(LlmGateway gateway) {
        ChatClient client = ChatClient.create(model);
        Prompt prompt = new Prompt(List.of(new UserMessage("hi")));
        return gateway.chat(AiCall.of("ASK", "ask-v3"), () -> client.prompt(prompt).call().chatResponse());
    }

    private static RuntimeException http(HttpStatus status) {
        return status.is5xxServerError()
                ? HttpServerErrorException.create(status, status.getReasonPhrase(), null, null, null)
                : HttpClientErrorException.create(status, status.getReasonPhrase(), null, null, null);
    }

    @Test
    void twoServiceUnavailableThenSuccess() {
        model.fail(http(HttpStatus.SERVICE_UNAVAILABLE)).fail(http(HttpStatus.SERVICE_UNAVAILABLE)).reply("ok");

        ChatResponse response = call(gateway(5));

        assertThat(response.getResult().getOutput().getText()).isEqualTo("ok");
        assertThat(model.prompts()).hasSize(3);
        assertThat(waits).hasSize(2);
        assertThat(waits.get(0).toMillis()).isBetween(250L, 500L);     // first wait: 500 ms with jitter
        assertThat(waits.get(1).toMillis()).isBetween(500L, 1000L);    // then doubled
        assertThat(rows.rows()).singleElement().satisfies(row -> {   // still one row, with the attempts
            assertThat(row.status()).isEqualTo(AiCallLog.Status.OK);
            assertThat(row.attempts()).isEqualTo(3);
        });
        assertThat(meters.counter(TpMetrics.LLM_RETRIES, "operation", "ASK", "reason", "5xx").count()).isEqualTo(2);
    }

    @Test
    void badRequestIsNotRetried() {
        model.fail(http(HttpStatus.BAD_REQUEST)).reply("never used");

        assertThatThrownBy(() -> call(gateway(5)))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.LLM_ERROR));
        assertThat(model.prompts()).hasSize(1);
        assertThat(waits).isEmpty();
        assertThat(rows.rows()).singleElement().satisfies(row -> {
            assertThat(row.status()).isEqualTo(AiCallLog.Status.ERROR);
            assertThat(row.attempts()).isEqualTo(1);
        });
    }

    @Test
    void givesUpAfterTwoRetries() {
        model.fail(new com.google.genai.errors.ServerException(503, "UNAVAILABLE", "overloaded"))
                .fail(new com.google.genai.errors.ClientException(429, "RESOURCE_EXHAUSTED", "quota"))
                .fail(new com.google.genai.errors.ServerException(500, "INTERNAL", "boom"))
                .reply("too late");

        assertThatThrownBy(() -> call(gateway(5))).isInstanceOf(ApiException.class);

        assertThat(model.prompts()).hasSize(3);
        assertThat(rows.rows().getFirst().attempts()).isEqualTo(3);
        assertThat(meters.counter(TpMetrics.LLM_RETRIES, "operation", "ASK", "reason", "429").count()).isEqualTo(1);
    }

    @Test
    void timeoutIsRetried() {
        model.replyAfter(Duration.ofSeconds(3), "slow").reply("fast");

        ChatResponse response = call(gateway(1));

        assertThat(response.getResult().getOutput().getText()).isEqualTo("fast");
        assertThat(meters.counter(TpMetrics.LLM_RETRIES, "operation", "ASK", "reason", "timeout").count())
                .isEqualTo(1);
    }

    @Test
    void classifiesErrors() {
        assertThat(RetryPolicy.retryReason(wrapped(http(HttpStatus.TOO_MANY_REQUESTS)))).contains("429");
        assertThat(RetryPolicy.retryReason(wrapped(http(HttpStatus.BAD_GATEWAY)))).contains("5xx");
        assertThat(RetryPolicy.retryReason(wrapped(new RuntimeException(
                new com.google.genai.errors.ServerException(503, "UNAVAILABLE", "x"))))).contains("5xx");
        assertThat(RetryPolicy.retryReason(wrapped(http(HttpStatus.UNAUTHORIZED)))).isEmpty();
        assertThat(RetryPolicy.retryReason(wrapped(new com.google.genai.errors.ClientException(400, "X", "y"))))
                .isEmpty();
        assertThat(RetryPolicy.retryReason(wrapped(new IllegalStateException("parse")))).isEmpty();
        assertThat(RetryPolicy.retryReason(new ApiException(ErrorCode.LLM_TIMEOUT))).contains("timeout");
    }

    @Test
    void backoffIsCappedAndJittered() {
        RetryPolicy policy = new RetryPolicy(5, Duration.ofMillis(500), Duration.ofMillis(4000));
        Random random = new Random(1);
        for (int n = 1; n <= 6; n++) {
            long cap = Math.min(500L << (n - 1), 4000);
            assertThat(policy.backoff(n, random).toMillis()).isBetween(cap / 2, cap);
        }
    }

    private static ApiException wrapped(RuntimeException cause) {
        return new ApiException(ErrorCode.LLM_ERROR, "x", cause);
    }
}
