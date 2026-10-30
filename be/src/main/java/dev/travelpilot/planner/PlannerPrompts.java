package dev.travelpilot.planner;

import dev.travelpilot.common.ai.PromptLoader;
import dev.travelpilot.common.ai.PromptLoader.VersionedPrompt;
import dev.travelpilot.recommendation.ScoredPlace;
import dev.travelpilot.trip.TripRequest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.stereotype.Component;

/** Renders the versioned planner and repair prompts from a {@link PlanContext}. */
@Component
public class PlannerPrompts {

    /** Fully rendered prompt; {@code id} (e.g. "planner-v1") is logged as promptVersion. */
    public record Rendered(String id, String system, String user) {}

    private final PromptLoader loader;
    private final String format = new BeanOutputConverter<>(TripPlanDraft.class).getFormat();

    public PlannerPrompts(PromptLoader loader) {
        this.loader = loader;
    }

    /** planner-v1 for a new trip; replan-v1 (planner + locked-day rules + instruction) when re-planning. */
    public Rendered plan(PlanContext ctx) {
        Rendered planner = basePlan(ctx);
        if (!ctx.isReplan()) {
            return planner;
        }
        VersionedPrompt replan = loader.load("replan");
        String instruction = ctx.instruction() == null || ctx.instruction().isBlank() ? "(none)" : ctx.instruction();
        return new Rendered(replan.id(),
                replan.renderSystem(Map.of("plannerSystem", planner.system())),
                replan.renderUser(Map.of("plannerUser", planner.user(), "instruction", instruction)));
    }

    private Rendered basePlan(PlanContext ctx) {
        VersionedPrompt prompt = loader.load("planner");
        return new Rendered(prompt.id(), prompt.renderSystem(systemParams(ctx)), prompt.renderUser(userParams(ctx)));
    }

    public Rendered repair(PlanContext ctx, String previousDraftJson, List<Violation> problems) {
        Rendered planner = plan(ctx);   // repairs of a re-plan keep the locked-day rules
        VersionedPrompt repair = loader.load("repair");
        String system = repair.renderSystem(Map.of("plannerSystem", planner.system()));
        String user = repair.renderUser(Map.of(
                "plannerUser", planner.user(),
                "draft", previousDraftJson == null ? "(the previous answer was not valid JSON)" : previousDraftJson,
                "problems", PlannerPromptFormatter.problems(problems)));
        return new Rendered(repair.id(), system, user);
    }

    private Map<String, Object> systemParams(PlanContext ctx) {
        TripRequest req = ctx.request();
        Map<String, Object> p = new HashMap<>();
        p.put("destination", req.destination().displayName());
        p.put("days", req.days());
        p.put("pace", req.pace().name());
        p.put("maxPerDay", req.pace().maxActivitiesPerDay());
        p.put("childRule", PlannerPromptFormatter.childRule(req));
        p.put("format", format);
        return p;
    }

    private Map<String, Object> userParams(PlanContext ctx) {
        TripRequest req = ctx.request();
        List<ScoredPlace> all = ctx.candidates().all().toList();
        return Map.of(
                "request", PlannerPromptFormatter.request(req),
                "weather", PlannerPromptFormatter.weather(req, ctx.forecast().days()),
                "candidates", PlannerPromptFormatter.candidates(all, req),
                "evidence", PlannerPromptFormatter.evidence(ctx.evidence()),
                "locked", PlannerPromptFormatter.locked(ctx.lockedDays()));
    }
}
