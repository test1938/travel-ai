package dev.travelpilot.common.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.travelpilot.common.ai.AiModels;
import org.junit.jupiter.api.Test;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.mock.env.MockEnvironment;

/** Provider switch: which model names are read, and which OpenAI options each kind of model accepts. */
class AiConfigTest {

    @Test
    void geminiIsTheDefaultProvider() {
        MockEnvironment env = new MockEnvironment()
                .withProperty("spring.ai.google.genai.chat.model", "gemini-2.5-flash")
                .withProperty("spring.ai.google.genai.embedding.text.model", "gemini-embedding-001");

        AiModels models = new AiModels(env);

        assertThat(models.chatProvider()).isEqualTo(AiModels.GEMINI);
        assertThat(models.chatModel()).isEqualTo("gemini-2.5-flash");
        assertThat(models.embeddingId()).isEqualTo("google-genai:gemini-embedding-001");
    }

    @Test
    void openAiProviderReadsOpenAiModels() {
        MockEnvironment env = new MockEnvironment()
                .withProperty("spring.ai.model.chat", "openai")
                .withProperty("spring.ai.model.embedding.text", "openai")
                .withProperty("spring.ai.openai.chat.model", "gpt-4.1-mini")
                .withProperty("spring.ai.openai.embedding.model", "text-embedding-3-small");

        AiModels models = new AiModels(env);

        assertThat(models.chatModel()).isEqualTo("gpt-4.1-mini");
        assertThat(models.embeddingId()).isEqualTo("openai:text-embedding-3-small");
    }

    @Test
    void theSelectedProviderMustHaveAKey() {
        MockEnvironment openAiWithoutKey = new MockEnvironment().withProperty("spring.ai.model.chat", "openai")
                .withProperty("spring.ai.model.embedding.text", "openai")
                .withProperty("spring.ai.google.genai.api-key", "gemini-key");
        assertThatThrownBy(() -> new AiModels(openAiWithoutKey).requireKeys())
                .hasMessageContaining("OPENAI_API_KEY is empty");

        MockEnvironment geminiWithoutKey = new MockEnvironment()
                .withProperty("spring.ai.model.embedding.text", "none")
                .withProperty("spring.ai.google.genai.api-key", "");
        assertThatThrownBy(() -> new AiModels(geminiWithoutKey).requireKeys())
                .hasMessageContaining("GEMINI_API_KEY");

        MockEnvironment openAiOnly = new MockEnvironment().withProperty("spring.ai.model.chat", "openai")
                .withProperty("spring.ai.model.embedding.text", "openai")
                .withProperty("spring.ai.openai.api-key", "sk-x");
        assertThatCode(() -> new AiModels(openAiOnly).requireKeys()).doesNotThrowAnyException();
        assertThatThrownBy(() -> new AiModels(new MockEnvironment().withProperty("spring.ai.model.embedding.text", ""))
                .requireKeys()).hasMessageContaining("AI_EMBEDDING_PROVIDER is empty");
        assertThatCode(() -> new AiModels(new MockEnvironment().withProperty("spring.ai.model.chat", "none")
                .withProperty("spring.ai.model.embedding.text", "none")).requireKeys()).doesNotThrowAnyException();
    }

    @Test
    void chatModelsGetTemperatureAndJsonMode() {
        OpenAiChatOptions options = AiConfig.openAi("gpt-4.1-mini", 0.3, 2048, true, "low").build();

        assertThat(options.getTemperature()).isEqualTo(0.3);
        assertThat(options.getMaxCompletionTokens()).isEqualTo(2048);
        assertThat(options.getReasoningEffort()).isNull();
        assertThat(options.getResponseFormat().getType()).isEqualTo(OpenAiChatModel.ResponseFormat.Type.JSON_OBJECT);
    }

    @Test
    void reasoningModelsGetReasoningEffortInsteadOfTemperature() {
        OpenAiChatOptions options = AiConfig.openAi("gpt-5-mini", 0.3, 8192, false, "low").build();

        assertThat(options.getTemperature()).isNull();
        assertThat(options.getReasoningEffort()).isEqualTo("low");
        assertThat(options.getResponseFormat()).isNull();
        assertThat(AiModels.isOpenAiReasoningModel("o4-mini")).isTrue();
        assertThat(AiModels.isOpenAiReasoningModel("gpt-4o-mini")).isFalse();
    }
}
