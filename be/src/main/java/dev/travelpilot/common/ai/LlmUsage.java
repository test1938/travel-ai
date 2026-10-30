package dev.travelpilot.common.ai;

import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;

/**
 * Model name and token counts of one response. {@code tokensOut} is what the provider bills as output: Gemini
 * reports thinking tokens outside the completion count, so it is taken from total - prompt when that is larger.
 */
public record LlmUsage(String model, int tokensIn, int tokensOut) {

    public static final LlmUsage NONE = new LlmUsage(null, 0, 0);

    public static LlmUsage of(ChatResponse response) {
        if (response == null || response.getMetadata() == null) {
            return NONE;
        }
        String model = response.getMetadata().getModel();
        model = model == null || model.isBlank() ? null : model;
        Usage usage = response.getMetadata().getUsage();
        if (usage == null) {
            return new LlmUsage(model, 0, 0);
        }
        int in = orZero(usage.getPromptTokens());
        int completion = orZero(usage.getCompletionTokens());
        int total = orZero(usage.getTotalTokens());
        return new LlmUsage(model, in, Math.max(completion, total - in));
    }

    private static int orZero(Integer n) {
        return n == null ? 0 : n;
    }
}
