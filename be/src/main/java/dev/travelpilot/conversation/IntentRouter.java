package dev.travelpilot.conversation;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import dev.travelpilot.common.Text;
import dev.travelpilot.common.ai.PromptLoader;
import dev.travelpilot.common.ai.AiCall;
import dev.travelpilot.common.ai.StructuredLlm;
import dev.travelpilot.common.error.ApiException;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/**
 * Classifies a chat message with the model (intent-v1, temperature 0). If the model fails or answers garbage,
 * a keyword heuristic decides, so chat keeps working during a provider outage.
 */
@Component
public class IntentRouter {

    private static final Logger log = LoggerFactory.getLogger(IntentRouter.class);

    /** Model output. */
    public record IntentResult(
            @JsonPropertyDescription("ASK_QUESTION, MODIFY_PLAN, SMALL_TALK or OUT_OF_SCOPE") Intent intent,
            @JsonPropertyDescription("Confidence between 0 and 1") double confidence) {}

    /** Decision plus whether it came from the model or the fallback heuristic. */
    public record Routed(Intent intent, double confidence, boolean fromModel) {}

    static final double HEURISTIC_CONFIDENCE = 0.3;
    private static final List<String> CHANGE_WORDS = List.of("doi ", "doi sang", "thay", "bo ", "bo di", "them",
            "giam", "tang", "bot ", "chuyen", "lap lai", "sua", "trong nha", "neu mua", "ngan sach");
    private static final List<String> GREETINGS = List.of("chao", "xin chao", "cam on", "hello", "hi", "thanks",
            "thank you", "ok", "tuyet");

    private final StructuredLlm llm;
    private final PromptLoader prompts;
    private final ChatOptions options;

    public IntentRouter(StructuredLlm llm, PromptLoader prompts,
            @Qualifier("classifierChatOptions") ChatOptions options) {
        this.llm = llm;
        this.prompts = prompts;
        this.options = options;
    }

    public Routed route(String message, ChatContext ctx) {
        PromptLoader.VersionedPrompt prompt = prompts.load("intent");
        try {
            StructuredLlm.Result<IntentResult> r = llm.call(AiCall.of("INTENT", prompt.id()), prompt.renderSystem(Map.of()),
                    prompt.renderUser(ctx.promptParams(message)), IntentResult.class, options);
            if (r.ok() && r.value().intent() != null) {
                return new Routed(r.value().intent(), r.value().confidence(), true);
            }
        } catch (ApiException e) {
            log.warn("Intent model call failed ({}), using keyword heuristic", e.code());
        }
        return new Routed(heuristic(message), HEURISTIC_CONFIDENCE, false);
    }

    /** Keyword fallback on accent-free lowercase text. */
    static Intent heuristic(String message) {
        String text = " " + plain(message) + " ";
        if (CHANGE_WORDS.stream().anyMatch(text::contains)) {
            return Intent.MODIFY_PLAN;
        }
        String trimmed = text.strip();
        if (trimmed.length() <= 25 && GREETINGS.stream().anyMatch(trimmed::startsWith)) {
            return Intent.SMALL_TALK;
        }
        return Intent.ASK_QUESTION;
    }

    static String plain(String text) {
        return Text.plain(text);
    }
}
