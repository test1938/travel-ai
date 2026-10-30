package dev.travelpilot.conversation;

import dev.travelpilot.common.ai.PromptLoader;
import dev.travelpilot.common.ai.StructuredLlm;
import dev.travelpilot.common.domain.PlaceTag;
import dev.travelpilot.trip.TripPlan;
import dev.travelpilot.trip.TripRequest;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/**
 * Extracts a {@link ChangeRequest} with change-v1 and sanitises it: unknown days, place ids that are not in
 * the plan, preferences outside the form list and non-positive budgets are dropped. Empty when the model did not
 * understand a concrete change (the chat then asks the traveler to rephrase).
 */
@Component
public class ChangeRequestExtractor {

    static final long MAX_BUDGET = 1_000_000_000L;

    private final StructuredLlm llm;
    private final PromptLoader prompts;
    private final ChatOptions options;

    public ChangeRequestExtractor(StructuredLlm llm, PromptLoader prompts,
            @Qualifier("classifierChatOptions") ChatOptions options) {
        this.llm = llm;
        this.prompts = prompts;
        this.options = options;
    }

    public Optional<ChangeRequest> extract(String message, ChatContext ctx) {
        PromptLoader.VersionedPrompt prompt = prompts.load("change");
        String allowed = TripRequest.ALLOWED_PREFERENCES.stream().map(PlaceTag::code).sorted()
                .collect(Collectors.joining(", "));
        StructuredLlm.Result<ChangeRequest> r = llm.call("CHANGE", prompt.renderSystem(Map.of("preferences", allowed)),
                prompt.renderUser(ctx.promptParams(message)), ChangeRequest.class, options);
        if (!r.ok() || !r.value().understood()) {
            return Optional.empty();
        }
        ChangeRequest clean = sanitize(r.value(), ctx.request(), ctx.plan());
        return clean.hasChanges() ? Optional.of(clean) : Optional.empty();
    }

    static ChangeRequest sanitize(ChangeRequest c, TripRequest req, TripPlan plan) {
        Integer day = c.dayIndex() != null && c.dayIndex() >= 1 && c.dayIndex() <= req.days() ? c.dayIndex() : null;
        ChangeRequest.Scope scope = c.scope() == ChangeRequest.Scope.DAY && day != null
                ? ChangeRequest.Scope.DAY : ChangeRequest.Scope.TRIP;
        Long budget = c.newBudgetVnd() != null && c.newBudgetVnd() > 0 && c.newBudgetVnd() <= MAX_BUDGET
                ? c.newBudgetVnd() : null;
        Set<String> planIds = plan.days().stream().flatMap(d -> d.items().stream())
                .map(TripPlan.PlanItem::placeId).collect(Collectors.toSet());
        List<String> exclude = c.excludePlaceIds().stream().filter(planIds::contains).distinct().toList();
        List<PlaceTag> add = c.addPreferences().stream().filter(TripRequest.ALLOWED_PREFERENCES::contains).toList();
        List<PlaceTag> remove = c.removePreferences().stream()
                .filter(TripRequest.ALLOWED_PREFERENCES::contains).toList();
        return new ChangeRequest(true, scope, scope == ChangeRequest.Scope.DAY ? day : null, budget, add, remove,
                c.preferIndoor(), exclude, c.instruction());
    }
}
