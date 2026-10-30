package dev.travelpilot.common.config;

import dev.travelpilot.common.ai.AiModels;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.google.genai.GoogleGenAiChatModel;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Chat client and per-use options. Every call has an output-token cap (OWASP LLM10): answers use the client
 * defaults, structured calls pass their own options. Gemini and OpenAI get their native options (JSON mode,
 * thinking or reasoning limits); other models (and test stubs) get portable options.
 */
@Configuration
public class AiConfig {

    static final double PLANNER_TEMPERATURE = 0.3;
    static final double ANSWER_TEMPERATURE = 0.3;

    private final ChatModel chatModel;
    private final AiModels models;
    private final TravelPilotProperties.Ai ai;
    private final String reasoningEffort;

    AiConfig(ChatModel chatModel, AiModels models, TravelPilotProperties props,
            @Value("${travelpilot.ai.openai-reasoning-effort:low}") String reasoningEffort) {
        this.chatModel = chatModel;
        models.requireKeys();
        this.models = models;
        this.ai = props.ai();
        this.reasoningEffort = reasoningEffort;
    }

    /** Plain client without a default system prompt: every feature sends its own versioned prompt. */
    @Bean
    ChatClient chatClient(ChatClient.Builder builder) {
        return builder.defaultOptions(options(ANSWER_TEMPERATURE, ai.maxOutputTokens(), false)).build();
    }

    /** Runs blocking LLM calls so they can be abandoned on timeout (see LlmGateway). */
    @Bean(destroyMethod = "close")
    ExecutorService llmExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }

    /** Options for structured planner calls: low temperature and JSON mode, so drafts parse on the first try. */
    @Bean
    ChatOptions plannerChatOptions() {
        return options(PLANNER_TEMPERATURE, ai.plannerMaxOutputTokens(), true).build();
    }

    /** Deterministic JSON calls (intent routing, change extraction, tool selection). */
    @Bean
    ChatOptions classifierChatOptions() {
        return options(0.0, ai.maxOutputTokens(), true).build();
    }

    private ChatOptions.Builder<?> options(double temperature, int maxOutputTokens, boolean json) {
        if (chatModel instanceof GoogleGenAiChatModel) {
            GoogleGenAiChatOptions.Builder b = GoogleGenAiChatOptions.builder()
                    .model(models.chatModel())
                    .temperature(temperature)
                    .maxOutputTokens(maxOutputTokens)
                    .thinkingBudget(ai.thinkingBudget());
            return json ? b.responseMimeType("application/json") : b;
        }
        if (chatModel instanceof OpenAiChatModel) {
            return openAi(models.chatModel(), temperature, maxOutputTokens, json, reasoningEffort);
        }
        return ChatOptions.builder().temperature(temperature).maxTokens(maxOutputTokens);
    }

    /**
     * max_completion_tokens works for every current OpenAI model (max_tokens is rejected by reasoning models).
     * Reasoning models keep their fixed temperature and count reasoning inside the cap, so it stays generous.
     */
    static OpenAiChatOptions.Builder openAi(String model, double temperature, int maxOutputTokens, boolean json,
            String reasoningEffort) {
        OpenAiChatOptions.Builder b = OpenAiChatOptions.builder().model(model).maxCompletionTokens(maxOutputTokens);
        if (AiModels.isOpenAiReasoningModel(model)) {
            b.reasoningEffort(reasoningEffort);
        } else {
            b.temperature(temperature);
        }
        if (json) {
            b.responseFormat(OpenAiChatModel.ResponseFormat.builder()
                    .type(OpenAiChatModel.ResponseFormat.Type.JSON_OBJECT)
                    .build());
        }
        return b;
    }
}
