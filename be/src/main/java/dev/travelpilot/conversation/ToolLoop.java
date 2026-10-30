package dev.travelpilot.conversation;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import dev.travelpilot.common.ai.PromptLoader;
import dev.travelpilot.common.ai.AiCall;
import dev.travelpilot.common.ai.AiCallLogger;
import dev.travelpilot.common.ai.StructuredLlm;
import dev.travelpilot.common.error.ApiException;
import dev.travelpilot.knowledge.DocType;
import dev.travelpilot.knowledge.Evidence;
import dev.travelpilot.tools.TravelTools;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * Bounded tool use for chat questions (ADR-007). The model only chooses tool calls as JSON; the backend validates
 * and runs them through the {@code @Tool} callbacks of {@link TravelTools}, and feeds results (or rejections) back.
 * At most {@value #MAX_ROUNDS} rounds of at most {@value #MAX_CALLS_PER_ROUND} calls. Any model failure just ends
 * the loop: the question is still answered from the knowledge base.
 */
@Component
public class ToolLoop {

    static final int MAX_ROUNDS = 3;
    static final int MAX_CALLS_PER_ROUND = 3;
    static final int MAX_LOGGED_VALUE = 40;
    private static final Logger log = LoggerFactory.getLogger(ToolLoop.class);

    /**
     * Cheap gate so ordinary questions do not pay for an extra model call: weather, availability, search or
     * suggestion words (accent-insensitive).
     */
    private static final Pattern MIGHT_NEED_TOOLS = Pattern.compile("\\b(thoi tiet|troi|mua|nhiet do|du bao|"
            + "lanh|nong|weather|rain|forecast|con cho|het cho|con ve|het ve|dat ve|dat cho|cho trong|available|"
            + "availability|tim|goi y|cho nao|o dau|dia diem nao|diem nao|trong nha|ngoai troi|indoor|outdoor|"
            + "search|suggest|recommend)\\b");

    /** What the model returns each round. */
    public record ToolPlan(
            @JsonPropertyDescription("Tool calls to make now, at most 3; empty when none is needed") List<Call> calls,
            @JsonPropertyDescription("true when no more tool calls are needed after these") boolean done) {}

    public record Call(
            @JsonPropertyDescription("Tool name") String name,
            @JsonPropertyDescription("Arguments as a JSON object matching the tool schema") Map<String, Object> arguments) {}

    public enum Status { OK, REJECTED }

    /** One executed (or rejected) call; {@code output} is the tool text or the rejection reason. */
    public record ToolResult(String name, Map<String, Object> arguments, Status status, String output,
            long latencyMs) {

        public boolean ok() {
            return status == Status.OK;
        }

        /** A successful result as a citable passage for the answer prompt. */
        public Evidence toEvidence() {
            TravelTools.ToolSource src = TravelTools.SOURCES.get(name);
            String title = src == null ? name : src.title();
            return new Evidence("tool:" + name, DocType.TOOL, null, List.of(), title, title + "\n" + output,
                    "tool", src == null ? null : src.url(), src == null ? null : src.license(), 1.0);
        }
    }

    private final StructuredLlm llm;
    private final PromptLoader prompts;
    private final ChatOptions options;
    private final JsonMapper json;
    private final Clock clock;
    private final Map<String, ToolCallback> callbacks;
    private final AiCallLogger calls;

    public ToolLoop(StructuredLlm llm, PromptLoader prompts, @Qualifier("classifierChatOptions") ChatOptions options,
            TravelTools tools, JsonMapper json, Clock clock, AiCallLogger calls) {
        this.llm = llm;
        this.calls = calls;
        this.prompts = prompts;
        this.options = options;
        this.json = json;
        this.clock = clock;
        this.callbacks = Arrays.stream(ToolCallbacks.from(tools))
                .collect(Collectors.toUnmodifiableMap(c -> c.getToolDefinition().name(), Function.identity()));
    }

    static boolean mightNeedTools(String message) {
        return MIGHT_NEED_TOOLS.matcher(IntentRouter.plain(message)).find();
    }

    public List<ToolResult> run(ChatContext ctx, String message) {
        if (!mightNeedTools(message)) {
            return List.of();
        }
        PromptLoader.VersionedPrompt prompt = prompts.load("tools");
        String system = prompt.renderSystem(Map.of(
                "today", LocalDate.now(clock).toString(),
                "destination", ctx.request().destination().displayName(),
                "destinationCode", ctx.request().destination().name(),
                "tools", toolCatalog()));
        List<ToolResult> results = new ArrayList<>();
        for (int round = 1; round <= MAX_ROUNDS; round++) {
            Map<String, Object> params = new HashMap<>(ctx.promptParams(message));
            params.put("results", resultsText(results));
            StructuredLlm.Result<ToolPlan> r;
            try {
                r = llm.call(AiCall.of("TOOLS", prompt.id()), system, prompt.renderUser(params), ToolPlan.class, options);
            } catch (ApiException e) {
                log.warn("Tool selection failed in round {} ({}), answering without more tools", round, e.code());
                break;
            }
            if (!r.ok() || r.value().calls() == null || r.value().calls().isEmpty()) {
                break;
            }
            r.value().calls().stream().limit(MAX_CALLS_PER_ROUND).map(this::execute).forEach(results::add);
            if (r.value().done()) {
                break;
            }
        }
        return results;
    }

    /** Runs one call through its @Tool callback; unknown tools and invalid arguments become REJECTED. */
    ToolResult execute(Call call) {
        Map<String, Object> args = call.arguments() == null ? Map.of() : call.arguments();
        long start = System.nanoTime();
        ToolCallback callback = call.name() == null ? null : callbacks.get(call.name());
        Status status;
        String output;
        if (callback == null) {
            status = Status.REJECTED;
            output = "Unknown tool '" + call.name() + "'. Available: " + String.join(", ", callbacks.keySet());
        } else {
            try {
                output = callback.call(json.writeValueAsString(args));
                status = Status.OK;
            } catch (RuntimeException e) {
                status = Status.REJECTED;
                output = rootMessage(e);
            }
        }
        long latencyMs = (System.nanoTime() - start) / 1_000_000;
        log.info("tool_call name={} args={} status={} latencyMs={}", call.name(), masked(args), status, latencyMs);
        calls.toolCall(call.name(), status == Status.OK, latencyMs);
        return new ToolResult(call.name(), args, status, output, latencyMs);
    }

    private String toolCatalog() {
        return callbacks.values().stream()
                .map(ToolCallback::getToolDefinition)
                .sorted((a, b) -> a.name().compareTo(b.name()))
                .map(ToolLoop::describe)
                .collect(Collectors.joining("\n"));
    }

    private static String describe(ToolDefinition d) {
        return "- " + d.name() + ": " + d.description() + "\n  arguments schema: " + d.inputSchema().strip();
    }

    private static String resultsText(List<ToolResult> results) {
        if (results.isEmpty()) {
            return "(none)";
        }
        StringBuilder sb = new StringBuilder();
        for (ToolResult r : results) {
            sb.append(r.name()).append(' ').append(r.arguments()).append(" -> ")
                    .append(r.ok() ? r.output() : "REJECTED: " + r.output()).append('\n');
        }
        return sb.toString().strip();
    }

    /** Arguments for the log: long values are cut so free text never ends up in logs in full. */
    static Map<String, Object> masked(Map<String, Object> args) {
        Map<String, Object> out = new LinkedHashMap<>();
        args.forEach((k, v) -> {
            String s = String.valueOf(v);
            out.put(k, s.length() > MAX_LOGGED_VALUE ? s.substring(0, MAX_LOGGED_VALUE) + "…" : v);
        });
        return out;
    }

    private static String rootMessage(Throwable e) {
        Throwable t = e;
        while (t.getCause() != null && t.getCause() != t) {
            t = t.getCause();
        }
        return t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage();
    }
}
