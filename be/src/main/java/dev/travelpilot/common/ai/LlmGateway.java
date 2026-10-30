package dev.travelpilot.common.ai;

import dev.travelpilot.common.ai.AiCallLog.Status;
import dev.travelpilot.common.config.TravelPilotProperties;
import dev.travelpilot.common.error.ApiException;
import dev.travelpilot.common.error.ErrorCode;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Every model call goes through here: hard timeout (LLM_TIMEOUT 504), provider failures mapped to LLM_ERROR 502
 * (so no ChatClient exception ever reaches a controller), and one ai_call_logs row per call.
 */
@Component
public class LlmGateway {

    private static final Logger log = LoggerFactory.getLogger(LlmGateway.class);

    private final ExecutorService llmExecutor;
    private final long timeoutMs;
    private final AiCallLogger calls;

    /** Without call logging, for unit tests. */
    public LlmGateway(ExecutorService llmExecutor, TravelPilotProperties props) {
        this(llmExecutor, props, AiCallLogger.none());
    }

    @Autowired
    public LlmGateway(ExecutorService llmExecutor, TravelPilotProperties props, AiCallLogger calls) {
        this.llmExecutor = llmExecutor;
        this.timeoutMs = TimeUnit.SECONDS.toMillis(props.ai().chatTimeoutSeconds());
        this.calls = calls;
    }

    /** Runs the call; the response always has a result (its text may still be null), otherwise LLM_ERROR. */
    public ChatResponse chat(AiCall call, Supplier<ChatResponse> modelCall) {
        long start = System.nanoTime();
        try {
            ChatResponse response = await(call.operation(), modelCall);
            if (response == null || response.getResult() == null) {
                throw new ApiException(ErrorCode.LLM_ERROR, ErrorCode.LLM_ERROR.defaultMessage(), null);
            }
            long ms = millisSince(start);
            log.debug("{} answered in {} ms", call.operation(), ms);
            calls.modelCall(call, LlmUsage.of(response), ms, 1, Status.OK, null);
            return response;
        } catch (ApiException e) {
            Status status = e.code() == ErrorCode.LLM_TIMEOUT ? Status.TIMEOUT : Status.ERROR;
            calls.modelCall(call, LlmUsage.NONE, millisSince(start), 1, status, e.code().name());
            throw e;
        }
    }

    private <T> T await(String operation, Supplier<T> modelCall) {
        Future<T> future = CompletableFuture.supplyAsync(modelCall, llmExecutor);
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

    private static long millisSince(long startNanos) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
    }
}
