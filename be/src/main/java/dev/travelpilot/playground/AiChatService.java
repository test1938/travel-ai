package dev.travelpilot.playground;

import dev.travelpilot.common.ai.LlmGateway;
import dev.travelpilot.common.error.ApiException;
import dev.travelpilot.common.error.ErrorCode;
import java.util.concurrent.TimeUnit;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Service;

/** Walking-skeleton chat: one free-form question to the model, through the LlmGateway. */
@Service
public class AiChatService {

    static final String SYSTEM_PROMPT = """
            You are TravelPilot, a travel assistant for Hanoi, Da Nang and Quang Ninh (Ha Long) in Vietnam.
            Answer briefly in the user's language. If you are not sure about prices or opening hours, say so.
            """;

    private final ChatClient chatClient;
    private final LlmGateway gateway;

    public AiChatService(ChatClient chatClient, LlmGateway gateway) {
        this.chatClient = chatClient;
        this.gateway = gateway;
    }

    public AiChatResponse chat(String message) {
        long start = System.nanoTime();
        ChatResponse response = gateway.call("CHAT",
                () -> chatClient.prompt().system(SYSTEM_PROMPT).user(message).call().chatResponse());
        String answer = response.getResult() == null ? null : response.getResult().getOutput().getText();
        if (answer == null) {
            throw new ApiException(ErrorCode.LLM_ERROR);
        }
        String model = response.getMetadata() == null ? null : response.getMetadata().getModel();
        return new AiChatResponse(answer, model, TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start));
    }
}
