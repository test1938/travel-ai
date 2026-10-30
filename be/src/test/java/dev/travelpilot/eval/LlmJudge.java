package dev.travelpilot.eval;

import dev.travelpilot.catalog.Place;
import dev.travelpilot.common.ai.AiCall;
import dev.travelpilot.common.ai.PromptLoader;
import dev.travelpilot.common.ai.StructuredLlm;
import dev.travelpilot.common.domain.PlaceTag;
import dev.travelpilot.common.error.ApiException;
import dev.travelpilot.conversation.ChatService;
import dev.travelpilot.knowledge.Source;
import dev.travelpilot.trip.TripPlan;
import dev.travelpilot.trip.TripPlan.PlanItem;
import dev.travelpilot.trip.TripRequest;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.ai.chat.prompt.ChatOptions;

/**
 * T8.03 LLM-as-judge (prompt judge-v1): scores groundedness and reason quality / helpfulness from 1 to 5 with
 * structured output. Plans are judged against catalog facts, answers against their cited sources. A judge
 * failure gives an empty score, never a failed run.
 */
public class LlmJudge implements Judge {

    /** What the model returns. */
    record Verdict(Integer groundedness, Integer reasonQuality, String comment) {}

    private final StructuredLlm llm;
    private final PromptLoader prompts;
    private final ChatOptions options;
    private final Map<String, Place> catalog;

    public LlmJudge(StructuredLlm llm, PromptLoader prompts, ChatOptions options, Map<String, Place> catalog) {
        this.llm = llm;
        this.prompts = prompts;
        this.options = options;
        this.catalog = catalog;
    }

    @Override
    public Score plan(TripRequest request, TripPlan plan) {
        Map<String, Object> params = params("itinerary");
        params.put("trip", trip(request));
        params.put("plan", items(plan));
        params.put("facts", facts(plan));
        return judge(params);
    }

    @Override
    public Score answer(String question, ChatService.ChatReply reply) {
        Map<String, Object> params = params("answer");
        params.put("question", question);
        params.put("answer", reply.reply());
        params.put("sources", reply.sources().isEmpty() ? "(none)" : reply.sources().stream()
                .map(LlmJudge::source).collect(Collectors.joining("\n")));
        return judge(params);
    }

    private Score judge(Map<String, Object> params) {
        PromptLoader.VersionedPrompt prompt = prompts.load("judge");
        try {
            StructuredLlm.Result<Verdict> r = llm.call(AiCall.of(EvalTarget.JUDGE_OPERATION, prompt.id()),
                    prompt.renderSystem(Map.of()), prompt.renderUser(params), Verdict.class, options);
            return r.ok() ? toScore(r.value()) : Score.failed("unparseable judge answer: " + r.error());
        } catch (ApiException e) {
            return Score.failed("judge call failed: " + e.code());
        }
    }

    /** Scores outside 1-5 mean the judge did not follow the format; they are dropped, not clamped. */
    static Score toScore(Verdict v) {
        if (!inRange(v.groundedness()) || !inRange(v.reasonQuality())) {
            return Score.failed("score out of range: " + v);
        }
        return new Score(v.groundedness(), v.reasonQuality(), v.comment());
    }

    private static boolean inRange(Integer score) {
        return score != null && score >= 1 && score <= 5;
    }

    private static Map<String, Object> params(String kind) {
        Map<String, Object> params = new LinkedHashMap<>();
        for (String key : new String[] {"trip", "question", "plan", "answer", "facts", "sources"}) {
            params.put(key, "(none)");
        }
        params.put("kind", kind);
        return params;
    }

    private static String trip(TripRequest r) {
        return "%s, %d days from %s, %d adults, children ages %s, budget %,d VND (tickets + meals), preferences %s, "
                .formatted(r.destination().displayName(), r.days(), r.startDate(), r.adults(), r.childrenAges(),
                        r.budgetVnd(), r.preferences().stream().map(PlaceTag::code).sorted().toList())
                + "pace " + r.pace() + ", notes " + r.notes();
    }

    private static String items(TripPlan plan) {
        StringBuilder sb = new StringBuilder();
        for (TripPlan.DayPlan d : plan.days()) {
            for (PlanItem i : d.items()) {
                sb.append("Day ").append(d.dayIndex()).append(' ').append(i.slot()).append(": ").append(i.name())
                        .append(" [").append(i.placeId()).append("] - reason: ").append(i.reason()).append('\n');
            }
        }
        sb.append("Budget total ").append(plan.budget().totalVnd()).append(" VND");
        return sb.toString();
    }

    private String facts(TripPlan plan) {
        return plan.days().stream().flatMap(d -> d.items().stream()).map(PlanItem::placeId).distinct()
                .map(catalog::get).filter(Objects::nonNull)
                .map(p -> ("[%s] %s: %s Type %s, tags %s, adult ticket %,d VND, child ticket %,d VND, "
                        + "children from %d, %s, %d min.").formatted(p.id(), p.name(), p.description(), p.type(),
                                p.tags().stream().map(PlaceTag::code).toList(), p.estCostVnd(), p.childCostVnd(),
                                p.childMinAge(), p.indoor() ? "indoor" : "outdoor", p.durationMin()))
                .collect(Collectors.joining("\n"));
    }

    private static String source(Source s) {
        return "[" + s.id() + "] " + s.title() + ": " + s.snippet();
    }
}
