package dev.travelpilot.planner;

import dev.travelpilot.common.ai.LlmGateway;
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
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * The LLM planner: renders planner-v1 / repair-v1, calls the model through {@link LlmGateway} (timeout and
 * error mapping) and parses the answer into a {@link TripPlanDraft}. A parse failure is returned as an attempt
 * without draft so the repair loop can ask again; provider errors propagate as ApiException.
 */
@Component
public class PlannerClient implements DraftPlanner {

    private static final Logger log = LoggerFactory.getLogger(PlannerClient.class);

    private final ChatClient chatClient;
    private final LlmGateway gateway;
    private final PlannerPrompts prompts;
    private final ChatOptions options;
    private final BeanOutputConverter<TripPlanDraft> converter = new BeanOutputConverter<>(TripPlanDraft.class);
    private final JsonMapper json = JsonMapper.builder().build();

    public PlannerClient(ChatClient chatClient, LlmGateway gateway, PlannerPrompts prompts,
            @Qualifier("plannerChatOptions") ChatOptions options) {
        this.chatClient = chatClient;
        this.gateway = gateway;
        this.prompts = prompts;
        this.options = options;
    }

    @Override
    public PlanAttempt plan(PlanContext ctx) {
        return call("PLAN", prompts.plan(ctx));
    }

    public PlanAttempt repair(PlanContext ctx, PlanAttempt previous, List<Violation> problems) {
        String previousJson = previous.draft() != null ? json.writeValueAsString(previous.draft()) : previous.rawJson();
        return call("REPAIR", prompts.repair(ctx, previousJson, problems));
    }

    private PlanAttempt call(String operation, PlannerPrompts.Rendered rendered) {
        Prompt prompt = new Prompt(List.of(new SystemMessage(rendered.system()), new UserMessage(rendered.user())),
                options);
        ChatResponse response = gateway.call(operation, () -> chatClient.prompt(prompt).call().chatResponse());
        String text = response.getResult() == null ? null : response.getResult().getOutput().getText();
        String model = response.getMetadata() == null ? null : response.getMetadata().getModel();
        Usage usage = response.getMetadata() == null ? null : response.getMetadata().getUsage();
        int in = usage == null || usage.getPromptTokens() == null ? 0 : usage.getPromptTokens();
        int out = usage == null || usage.getCompletionTokens() == null ? 0 : usage.getCompletionTokens();
        try {
            TripPlanDraft draft = text == null ? null : converter.convert(text);
            if (draft == null) {
                return new PlanAttempt(null, "empty answer", rendered.id(), model, in, out, text);
            }
            return new PlanAttempt(draft, null, rendered.id(), model, in, out, text);
        } catch (RuntimeException e) {
            log.warn("{}: model answer is not a valid draft: {}", operation, e.getMessage());
            return new PlanAttempt(null, e.getMessage(), rendered.id(), model, in, out, text);
        }
    }
}
