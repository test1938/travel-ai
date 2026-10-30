package dev.travelpilot.common.ai;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.stereotype.Component;

/**
 * One structured model call: pre-rendered system + user messages, the JSON schema of {@code T} appended to the
 * system prompt, timeout/error mapping via {@link LlmGateway}, parsing with {@link BeanOutputConverter}.
 * Provider errors propagate as ApiException; an unparseable answer is returned with {@code value == null}.
 */
@Component
public class StructuredLlm {

    private static final Logger log = LoggerFactory.getLogger(StructuredLlm.class);

    /** Parsed value (or null with an error) plus usage for logging. */
    public record Result<T>(T value, String error, String raw, String model, int tokensIn, int tokensOut) {

        public boolean ok() {
            return value != null;
        }
    }

    private final ChatClient chatClient;
    private final LlmGateway gateway;

    public StructuredLlm(ChatClient chatClient, LlmGateway gateway) {
        this.chatClient = chatClient;
        this.gateway = gateway;
    }

    public <T> Result<T> call(String operation, String system, String user, Class<T> type, ChatOptions options) {
        BeanOutputConverter<T> converter = new BeanOutputConverter<>(type);
        Prompt prompt = new Prompt(List.of(new SystemMessage(system + "\n\n" + converter.getFormat()),
                new UserMessage(user)), options);
        ChatResponse response = gateway.call(operation, () -> chatClient.prompt(prompt).call().chatResponse());
        String text = response.getResult() == null ? null : response.getResult().getOutput().getText();
        String model = response.getMetadata() == null ? null : response.getMetadata().getModel();
        Usage usage = response.getMetadata() == null ? null : response.getMetadata().getUsage();
        int in = usage == null || usage.getPromptTokens() == null ? 0 : usage.getPromptTokens();
        int out = usage == null || usage.getCompletionTokens() == null ? 0 : usage.getCompletionTokens();
        try {
            T value = text == null ? null : converter.convert(text);
            return new Result<>(value, value == null ? "empty answer" : null, text, model, in, out);
        } catch (RuntimeException e) {
            log.warn("{}: answer is not valid {}: {}", operation, type.getSimpleName(), e.getMessage());
            return new Result<>(null, e.getMessage(), text, model, in, out);
        }
    }
}
