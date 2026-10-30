package dev.travelpilot.playground;

import dev.travelpilot.common.config.TravelPilotProperties;
import dev.travelpilot.common.error.ApiException;
import dev.travelpilot.common.error.ErrorCode;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Service;

/** Walking-skeleton chat: one free-form question to the model, with a hard timeout and mapped errors. */
@Service
public class AiChatService {

    private final ChatClient chatClient;
    private final ExecutorService llmExecutor;
    private final long timeoutMs;

    public AiChatService(ChatClient chatClient, ExecutorService llmExecutor, TravelPilotProperties props) {
        this.chatClient = chatClient;
        this.llmExecutor = llmExecutor;
        this.timeoutMs = TimeUnit.SECONDS.toMillis(props.ai().chatTimeoutSeconds());
    }

    public AiChatResponse chat(String message) {
        long start = System.nanoTime();
        Future<ChatResponse> call = CompletableFuture.supplyAsync(
                () -> chatClient.prompt().user(message).call().chatResponse(), llmExecutor);
        ChatResponse response;
        try {
            response = call.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            call.cancel(true);
            throw new ApiException(ErrorCode.LLM_TIMEOUT, ErrorCode.LLM_TIMEOUT.defaultMessage(), e);
        } catch (ExecutionException e) {
            throw new ApiException(ErrorCode.LLM_ERROR, ErrorCode.LLM_ERROR.defaultMessage(), e.getCause());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ApiException(ErrorCode.LLM_ERROR, ErrorCode.LLM_ERROR.defaultMessage(), e);
        }
        if (response == null || response.getResult() == null || response.getResult().getOutput().getText() == null) {
            throw new ApiException(ErrorCode.LLM_ERROR, ErrorCode.LLM_ERROR.defaultMessage(), null);
        }
        String model = response.getMetadata() != null ? response.getMetadata().getModel() : null;
        long latencyMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
        return new AiChatResponse(response.getResult().getOutput().getText(), model, latencyMs);
    }
}
