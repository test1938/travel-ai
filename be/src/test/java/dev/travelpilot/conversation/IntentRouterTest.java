package dev.travelpilot.conversation;

import static org.assertj.core.api.Assertions.assertThat;

import dev.travelpilot.support.StubChatModel;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.ai.chat.prompt.ChatOptions;

class IntentRouterTest {

    private final StubChatModel model = new StubChatModel();
    private final IntentRouter router = new IntentRouter(ConversationFixtures.llm(model),
            ConversationFixtures.prompts(), ChatOptions.builder().temperature(0.0).build());

    @ParameterizedTest
    @EnumSource(Intent.class)
    void usesTheModelAnswerForEachIntent(Intent intent) {
        model.reply("{\"intent\":\"" + intent + "\",\"confidence\":0.9}");

        IntentRouter.Routed r = router.route("tin nhắn", ConversationFixtures.context());

        assertThat(r.intent()).isEqualTo(intent);
        assertThat(r.fromModel()).isTrue();
        assertThat(r.confidence()).isEqualTo(0.9);
    }

    @Test
    void promptCarriesCurrentConstraintsPlanAndRecentHistory() {
        model.reply("{\"intent\":\"MODIFY_PLAN\",\"confidence\":0.8}");
        ChatMessage earlier = new ChatMessage(1, ChatMessage.Role.USER, "Giảm còn 5 triệu nhé", null, null,
                OffsetDateTime.now());

        router.route("Ngày 2 trời mưa, đổi sang trong nhà", ConversationFixtures.context(List.of(earlier)));

        String sent = model.prompts().getFirst().getContents();
        assertThat(sent).contains("<trip>").contains("6000000 VND")
                .contains("Day 3 (2026-11-14): MORNING Sun World Bà Nà Hills [danang-way-359114064]")
                .contains("USER: Giảm còn 5 triệu nhé")
                .contains("<message>Ngày 2 trời mưa, đổi sang trong nhà</message>")
                .contains("\"intent\"");   // JSON schema of IntentResult
    }

    @Test
    void garbageAnswerFallsBackToHeuristic() {
        model.reply("I think it is a question");
        IntentRouter.Routed r = router.route("Giảm ngân sách xuống 3 triệu", ConversationFixtures.context());
        assertThat(r.fromModel()).isFalse();
        assertThat(r.intent()).isEqualTo(Intent.MODIFY_PLAN);
        assertThat(r.confidence()).isEqualTo(IntentRouter.HEURISTIC_CONFIDENCE);
    }

    @Test
    void providerOutageFallsBackToHeuristic() {
        model.fail(new RuntimeException("503"));
        assertThat(router.route("Bà Nà có hợp với bé 6 tuổi không?", ConversationFixtures.context()).intent())
                .isEqualTo(Intent.ASK_QUESTION);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "Ngày 2 trời mưa, đổi sang hoạt động trong nhà | MODIFY_PLAN",
        "Giảm ngân sách xuống 3 triệu | MODIFY_PLAN",
        "Bỏ Bà Nà đi | MODIFY_PLAN",
        "Thêm một bữa hải sản | MODIFY_PLAN",
        "Cầu Rồng phun lửa lúc mấy giờ? | ASK_QUESTION",
        "Vì sao chọn chỗ này? | ASK_QUESTION",
        "Cảm ơn nhé | SMALL_TALK",
        "Xin chào | SMALL_TALK",
    })
    void heuristicHandlesCommonVietnameseMessages(String message, Intent expected) {
        assertThat(IntentRouter.heuristic(message)).isEqualTo(expected);
    }

    @Test
    void plainTextRemovesAccents() {
        assertThat(IntentRouter.plain("Đổi sang TRONG NHÀ!")).isEqualTo("doi sang trong nha");
    }
}
