package dev.travelpilot.common.ai;

import dev.travelpilot.common.ai.AiCallLog.Status;
import dev.travelpilot.common.config.TravelPilotProperties;
import dev.travelpilot.common.error.ApiException;
import dev.travelpilot.common.error.ErrorCode;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;
import java.util.random.RandomGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Every model call goes through here: hard timeout per attempt (LLM_TIMEOUT 504), retries of transient failures
 * ({@link RetryPolicy}), provider failures mapped to LLM_ERROR 502 (so no ChatClient exception ever reaches a
 * controller), and one ai_call_logs row per call.
 */
@Component
public class LlmGateway {

    private static final Logger log = LoggerFactory.getLogger(LlmGateway.class);

    /** Waits between attempts; replaced in tests so they do not sleep. */
    @FunctionalInterface
    interface Sleeper {
        void sleep(Duration duration) throws InterruptedException;
    }

    private final ExecutorService llmExecutor;
    private final long timeoutMs;
    private final AiCallLogger calls;
    private final RetryPolicy retry;
    private final Sleeper sleeper;
    private final RandomGenerator random;

    /** Without call logging, for unit tests. */
    public LlmGateway(ExecutorService llmExecutor, TravelPilotProperties props) {
        this(llmExecutor, props, AiCallLogger.none());
    }

    @Autowired
    public LlmGateway(ExecutorService llmExecutor, TravelPilotProperties props, AiCallLogger calls) {
        this(llmExecutor, props, calls, Thread::sleep, RandomGenerator.getDefault());
    }

    LlmGateway(ExecutorService llmExecutor, TravelPilotProperties props, AiCallLogger calls, Sleeper sleeper,
            RandomGenerator random) {
        this.llmExecutor = llmExecutor;
        this.timeoutMs = TimeUnit.SECONDS.toMillis(props.ai().chatTimeoutSeconds());
        this.calls = calls;
        this.retry = RetryPolicy.of(props.ai().retry());
        this.sleeper = sleeper;
        this.random = random;
    }

    /**
     * Runs the call, retrying 429/5xx/timeouts with backoff (T7.04). The response always has a result (its text
     * may still be null), otherwise LLM_ERROR. One ai_call_logs row per call, with the number of attempts.
     */
    public ChatResponse chat(AiCall call, Supplier<ChatResponse> modelCall) {
        long start = System.nanoTime();
        for (int attempt = 1; ; attempt++) {
            try {
                ChatResponse response = await(call.operation(), modelCall);
                if (response == null || response.getResult() == null) {
                    throw new ApiException(ErrorCode.LLM_ERROR, ErrorCode.LLM_ERROR.defaultMessage(), null);
                }
                long ms = millisSince(start);
                log.debug("{} answered in {} ms after {} attempt(s)", call.operation(), ms, attempt);
                calls.modelCall(call, LlmUsage.of(response), ms, attempt, Status.OK, null);
                return response;
            } catch (ApiException e) {
                Optional<String> reason = attempt <= retry.maxRetries()
                        ? RetryPolicy.retryReason(e) : Optional.empty();
                if (reason.isEmpty()) {
                    Status status = e.code() == ErrorCode.LLM_TIMEOUT ? Status.TIMEOUT : Status.ERROR;
                    calls.modelCall(call, LlmUsage.NONE, millisSince(start), attempt, status, e.code().name());
                    throw e;
                }
                Duration wait = retry.backoff(attempt, random);
                log.warn("{} failed ({}), retry {}/{} in {} ms", call.operation(), reason.get(), attempt,
                        retry.maxRetries(), wait.toMillis());
                calls.retry(call, reason.get());
                pause(wait, e);
            }
        }
    }

    private void pause(Duration wait, ApiException cause) {
        try {
            sleeper.sleep(wait);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw cause;
        }
    }

    private <T> T await(String operation, Supplier<T> modelCall) {
        Future<T> future = CompletableFuture.supplyAsync(withMdc(modelCall), llmExecutor);
        try {
            return future.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            log.warn("{} timed out after {} ms", operation, timeoutMs);
            throw new ApiException(ErrorCode.LLM_TIMEOUT, ErrorCode.LLM_TIMEOUT.defaultMessage(), e);
        } catch (ExecutionException e) {
            throw new ApiException(ErrorCode.LLM_ERROR, ErrorCode.LLM_ERROR.defaultMessage(), e.getCause());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ApiException(ErrorCode.LLM_ERROR, ErrorCode.LLM_ERROR.defaultMessage(), e);
        }
    }

    /** Model calls run on another thread; copy the MDC so their log lines keep the request id. */
    private static <T> Supplier<T> withMdc(Supplier<T> task) {
        Map<String, String> context = MDC.getCopyOfContextMap();
        return () -> {
            if (context != null) {
                MDC.setContextMap(context);
            }
            try {
                return task.get();
            } finally {
                MDC.clear();
            }
        };
    }

    private static long millisSince(long startNanos) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
    }
}
