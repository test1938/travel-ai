package dev.travelpilot.playground;

import dev.travelpilot.common.ai.AiCall;
import dev.travelpilot.common.ai.LlmGateway;
import dev.travelpilot.common.error.ApiException;
import dev.travelpilot.common.error.ErrorCode;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/** Walking-skeleton chat: one free-form question to the model, through the LlmGateway. */
@Profile("dev")
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
        // Messages, not templates: a "{" typed by the user must not be parsed as a placeholder.
        Prompt prompt = new Prompt(List.of(new SystemMessage(SYSTEM_PROMPT), new UserMessage(message)));
        ChatResponse response = gateway.chat(AiCall.of("CHAT", "playground"), () -> chatClient.prompt(prompt).call().chatResponse());
        String answer = response.getResult() == null ? null : response.getResult().getOutput().getText();
        if (answer == null) {
            throw new ApiException(ErrorCode.LLM_ERROR);
        }
        String model = response.getMetadata() == null ? null : response.getMetadata().getModel();
        return new AiChatResponse(answer, model, TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start));
    }
}
