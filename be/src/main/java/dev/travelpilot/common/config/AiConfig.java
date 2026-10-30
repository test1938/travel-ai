package dev.travelpilot.common.config;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.google.genai.GoogleGenAiChatModel;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Chat client and per-use options. Every call has an output-token cap (OWASP LLM10): answers use the client
 * defaults, structured calls pass their own options.
 */
@Configuration
public class AiConfig {

    static final double PLANNER_TEMPERATURE = 0.3;
    static final double ANSWER_TEMPERATURE = 0.3;

    /** Plain client without a default system prompt: every feature sends its own versioned prompt. */
    @Bean
    ChatClient chatClient(ChatClient.Builder builder, ChatModel chatModel, TravelPilotProperties props,
            @Value("${spring.ai.google.genai.chat.model:}") String geminiModel) {
        TravelPilotProperties.Ai ai = props.ai();
        ChatOptions.Builder<?> defaults = chatModel instanceof GoogleGenAiChatModel
                ? GoogleGenAiChatOptions.builder()
                        .model(geminiModel)
                        .temperature(ANSWER_TEMPERATURE)
                        .maxOutputTokens(ai.maxOutputTokens())
                        .thinkingBudget(ai.thinkingBudget())
                : ChatOptions.builder().temperature(ANSWER_TEMPERATURE).maxTokens(ai.maxOutputTokens());
        return builder.defaultOptions(defaults).build();
    }

    /** Runs blocking LLM calls so they can be abandoned on timeout (see LlmGateway). */
    @Bean(destroyMethod = "close")
    ExecutorService llmExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }

    /**
     * Options for structured planner calls: low temperature and, on Gemini, JSON response mode so the draft
     * parses on the first try more often. Other models (and test stubs) get portable options.
     */
    @Bean
    ChatOptions plannerChatOptions(ChatModel chatModel, TravelPilotProperties props,
            @Value("${spring.ai.google.genai.chat.model:}") String geminiModel) {
        return jsonOptions(chatModel, geminiModel, PLANNER_TEMPERATURE, props.ai().plannerMaxOutputTokens(),
                props.ai().thinkingBudget());
    }

    /** Deterministic JSON calls (intent routing, change extraction, tool selection). */
    @Bean
    ChatOptions classifierChatOptions(ChatModel chatModel, TravelPilotProperties props,
            @Value("${spring.ai.google.genai.chat.model:}") String geminiModel) {
        return jsonOptions(chatModel, geminiModel, 0.0, props.ai().maxOutputTokens(), props.ai().thinkingBudget());
    }

    private static ChatOptions jsonOptions(ChatModel chatModel, String geminiModel, double temperature,
            int maxOutputTokens, int thinkingBudget) {
        if (chatModel instanceof GoogleGenAiChatModel) {
            return GoogleGenAiChatOptions.builder()
                    .model(geminiModel)
                    .temperature(temperature)
                    .maxOutputTokens(maxOutputTokens)
                    .thinkingBudget(thinkingBudget)
                    .responseMimeType("application/json")
                    .build();
        }
        return ChatOptions.builder().temperature(temperature).maxTokens(maxOutputTokens).build();
    }
}
