package dev.travelpilot.conversation;

import dev.travelpilot.common.ai.LlmGateway;
import dev.travelpilot.common.ai.PromptLoader;
import dev.travelpilot.common.ai.StructuredLlm;
import dev.travelpilot.planner.Assembler;
import dev.travelpilot.planner.PlanValidator;
import dev.travelpilot.planner.PlannerFixtures;
import dev.travelpilot.support.StubChatModel;
import dev.travelpilot.support.TestProps;
import dev.travelpilot.trip.TripPlan;
import dev.travelpilot.trip.TripPlan.PlanMeta;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import org.springframework.ai.chat.client.ChatClient;

/** Family trip (version 1 = PlannerFixtures.validDraft) and helpers for chat tests. */
final class ConversationFixtures {

    static final UUID TRIP_ID = UUID.fromString("11111111-2222-3333-4444-555555555555");

    private ConversationFixtures() {}

    static TripPlan familyPlan() {
        var ctx = PlannerFixtures.context();
        var draft = PlannerFixtures.validDraft();
        return new Assembler().assemble(draft, ctx, new PlanValidator().validate(draft, ctx).soft(), List.of(),
                new PlanMeta("planner-v1", "stub", false, 0, false, 1000)).withIdentity(TRIP_ID, 1);
    }

    static ChatContext context(List<ChatMessage> window) {
        return new ChatContext(TRIP_ID, PlannerFixtures.family(), familyPlan(), window);
    }

    static ChatContext context() {
        return context(List.of());
    }

    static StructuredLlm llm(StubChatModel model) {
        return new StructuredLlm(ChatClient.create(model),
                new LlmGateway(Executors.newVirtualThreadPerTaskExecutor(), TestProps.defaults()));
    }

    static PromptLoader prompts() {
        return new PromptLoader(TestProps.defaults());
    }
}
