package dev.travelpilot.common.ai;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Which provider and model serve chat and embeddings. The provider is chosen in config with
 * {@code spring.ai.model.chat} / {@code spring.ai.model.embedding.text} (env {@code AI_PROVIDER},
 * {@code AI_EMBEDDING_PROVIDER}): {@value #GEMINI} or {@value #OPENAI}. Only the selected provider's
 * auto-configuration runs, so the other one needs no API key.
 */
@Component
public class AiModels {

    public static final String GEMINI = "google-genai";
    public static final String OPENAI = "openai";

    private final String chatProvider;
    private final String chatModel;
    private final String embeddingProvider;
    private final String embeddingModel;
    private final boolean geminiKey;
    private final boolean openAiKey;

    public AiModels(Environment env) {
        this.chatProvider = env.getProperty("spring.ai.model.chat", GEMINI);
        this.chatModel = OPENAI.equals(chatProvider)
                ? env.getProperty("spring.ai.openai.chat.model", "")
                : env.getProperty("spring.ai.google.genai.chat.model", "");
        this.embeddingProvider = env.getProperty("spring.ai.model.embedding.text", GEMINI);
        this.embeddingModel = OPENAI.equals(embeddingProvider)
                ? env.getProperty("spring.ai.openai.embedding.model", "")
                : env.getProperty("spring.ai.google.genai.embedding.text.model", "");
        this.geminiKey = isKey(env.getProperty("spring.ai.google.genai.api-key"));
        this.openAiKey = isKey(env.getProperty("spring.ai.openai.api-key"));
    }

    /**
     * Fails at startup with a clear message when the selected provider has no key, instead of a 401 on the first
     * trip. Providers other than Gemini and OpenAI (tests use "none") need nothing.
     */
    public void requireKeys() {
        if (embeddingProvider.isBlank()) {
            throw new IllegalStateException("AI_EMBEDDING_PROVIDER is empty: delete that line from .env (embeddings then"
                    + " follow AI_PROVIDER) or set it to google-genai or openai");
        }
        for (String provider : new String[] {chatProvider, embeddingProvider}) {
            if (GEMINI.equals(provider) && !geminiKey) {
                throw new IllegalStateException("AI_PROVIDER uses Gemini but GEMINI_API_KEY (or AI_API_KEY) is empty:"
                        + " set it in .env, or switch with AI_PROVIDER=openai and OPENAI_API_KEY");
            }
            if (OPENAI.equals(provider) && !openAiKey) {
                throw new IllegalStateException("AI_PROVIDER uses OpenAI but OPENAI_API_KEY is empty: set it in .env,"
                        + " or switch with AI_PROVIDER=google-genai and GEMINI_API_KEY");
            }
        }
    }

    private static boolean isKey(String value) {
        return value != null && !value.isBlank();
    }

    public String chatProvider() {
        return chatProvider;
    }

    public String chatModel() {
        return chatModel;
    }

    public String embeddingModel() {
        return embeddingModel;
    }

    /**
     * Identifies the vector space, e.g. {@code openai:text-embedding-3-small}. Vectors from different models are
     * not comparable even at the same dimension, so the knowledge base is re-embedded when this changes.
     */
    public String embeddingId() {
        return embeddingProvider + ":" + embeddingModel;
    }

    /**
     * OpenAI reasoning models (gpt-5 family, o-series) reject a custom temperature and spend hidden reasoning
     * tokens; chat models such as gpt-4.1-mini accept both.
     */
    public static boolean isOpenAiReasoningModel(String model) {
        return model != null && (model.startsWith("gpt-5") || model.matches("o\\d.*"));
    }
}
