package dev.travelpilot.common.ai;

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
import org.springframework.stereotype.Component;

/**
 * Every model call goes through here: hard timeout (LLM_TIMEOUT 504) and provider failures mapped to
 * LLM_ERROR 502, so no ChatClient exception ever reaches a controller.
 */
@Component
public class LlmGateway {

    private static final Logger log = LoggerFactory.getLogger(LlmGateway.class);

    private final ExecutorService llmExecutor;
    private final long timeoutMs;

    public LlmGateway(ExecutorService llmExecutor, TravelPilotProperties props) {
        this.llmExecutor = llmExecutor;
        this.timeoutMs = TimeUnit.SECONDS.toMillis(props.ai().chatTimeoutSeconds());
    }

    public <T> T call(String operation, Supplier<T> modelCall) {
        long start = System.nanoTime();
        Future<T> future = CompletableFuture.supplyAsync(modelCall, llmExecutor);
        try {
            T result = future.get(timeoutMs, TimeUnit.MILLISECONDS);
            if (result == null) {
                throw new ApiException(ErrorCode.LLM_ERROR, ErrorCode.LLM_ERROR.defaultMessage(), null);
            }
            log.debug("{} answered in {} ms", operation, TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start));
            return result;
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
}
