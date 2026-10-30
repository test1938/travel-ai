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

@Configuration
public class AiConfig {

    static final double PLANNER_TEMPERATURE = 0.3;

    /** Plain client without a default system prompt: every feature sends its own versioned prompt. */
    @Bean
    ChatClient chatClient(ChatClient.Builder builder) {
        return builder.build();
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
    ChatOptions plannerChatOptions(ChatModel chatModel,
            @Value("${spring.ai.google.genai.chat.model:}") String geminiModel) {
        return jsonOptions(chatModel, geminiModel, PLANNER_TEMPERATURE);
    }

    /** Deterministic JSON calls (intent routing, change extraction). */
    @Bean
    ChatOptions classifierChatOptions(ChatModel chatModel,
            @Value("${spring.ai.google.genai.chat.model:}") String geminiModel) {
        return jsonOptions(chatModel, geminiModel, 0.0);
    }

    private static ChatOptions jsonOptions(ChatModel chatModel, String geminiModel, double temperature) {
        if (chatModel instanceof GoogleGenAiChatModel) {
            return GoogleGenAiChatOptions.builder()
                    .model(geminiModel)
                    .temperature(temperature)
                    .responseMimeType("application/json")
                    .build();
        }
        return ChatOptions.builder().temperature(temperature).build();
    }
}
