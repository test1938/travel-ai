package dev.travelpilot.common.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.google.genai.GoogleGenAiChatModel;
import org.springframework.ai.google.genai.text.GoogleGenAiTextEmbeddingModel;
import org.springframework.ai.model.google.genai.autoconfigure.chat.GoogleGenAiChatAutoConfiguration;
import org.springframework.ai.model.google.genai.autoconfigure.embedding.GoogleGenAiEmbeddingConnectionAutoConfiguration;
import org.springframework.ai.model.google.genai.autoconfigure.embedding.GoogleGenAiTextEmbeddingAutoConfiguration;
import org.springframework.ai.model.openai.autoconfigure.OpenAiAudioSpeechAutoConfiguration;
import org.springframework.ai.model.openai.autoconfigure.OpenAiAudioTranscriptionAutoConfiguration;
import org.springframework.ai.model.openai.autoconfigure.OpenAiChatAutoConfiguration;
import org.springframework.ai.model.openai.autoconfigure.OpenAiEmbeddingAutoConfiguration;
import org.springframework.ai.model.openai.autoconfigure.OpenAiImageAutoConfiguration;
import org.springframework.ai.model.openai.autoconfigure.OpenAiModerationAutoConfiguration;
import org.springframework.ai.model.tool.autoconfigure.ToolCallingAutoConfiguration;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.retry.autoconfigure.SpringAiRetryAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * The real application.yml with only the env variables a user sets: AI_PROVIDER selects exactly one chat model
 * and one embedding model, and the other provider needs no key. Fake keys: nothing is called.
 */
class AiProviderSwitchTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withConfiguration(AutoConfigurations.of(SpringAiRetryAutoConfiguration.class,
                    ToolCallingAutoConfiguration.class, GoogleGenAiChatAutoConfiguration.class,
                    GoogleGenAiEmbeddingConnectionAutoConfiguration.class,
                    GoogleGenAiTextEmbeddingAutoConfiguration.class, OpenAiChatAutoConfiguration.class,
                    OpenAiEmbeddingAutoConfiguration.class, OpenAiImageAutoConfiguration.class,
                    OpenAiAudioSpeechAutoConfiguration.class, OpenAiAudioTranscriptionAutoConfiguration.class,
                    OpenAiModerationAutoConfiguration.class))
            .withUserConfiguration(UnusedGeminiEmbeddingConfig.class)
            .withBean(MeterRegistry.class, SimpleMeterRegistry::new)
            // a developer's .env must not leak into the test: every variable is set explicitly
            .withPropertyValues("AI_EMBEDDING_PROVIDER=", "GEMINI_API_KEY=", "AI_API_KEY=", "OPENAI_API_KEY=");

    @Test
    void openAiNeedsOnlyAnOpenAiKey() {
        runner.withPropertyValues("AI_PROVIDER=openai", "AI_EMBEDDING_PROVIDER=openai", "OPENAI_API_KEY=sk-test")
                .run(ctx -> {
                    assertThat(ctx).hasNotFailed();
                    assertThat(ctx.getBean(ChatModel.class)).isInstanceOf(OpenAiChatModel.class);
                    assertThat(ctx.getBean(EmbeddingModel.class)).isInstanceOf(OpenAiEmbeddingModel.class);
                    assertThat(ctx).doesNotHaveBean(GoogleGenAiChatModel.class);
                });
    }

    @Test
    void geminiNeedsOnlyAGeminiKey() {
        runner.withPropertyValues("AI_PROVIDER=google-genai", "AI_EMBEDDING_PROVIDER=google-genai",
                        "GEMINI_API_KEY=test-key")
                .run(ctx -> {
                    assertThat(ctx).hasNotFailed();
                    assertThat(ctx.getBean(ChatModel.class)).isInstanceOf(GoogleGenAiChatModel.class);
                    assertThat(ctx.getBean(EmbeddingModel.class)).isInstanceOf(GoogleGenAiTextEmbeddingModel.class);
                    assertThat(ctx).doesNotHaveBean(OpenAiChatModel.class);
                });
    }
}
