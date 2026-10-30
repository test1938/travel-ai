package dev.travelpilot.conversation;

import static org.assertj.core.api.Assertions.assertThat;

import dev.travelpilot.common.domain.PlaceTag;
import dev.travelpilot.support.Personas;
import dev.travelpilot.support.StubChatModel;
import dev.travelpilot.trip.TripRequest;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.prompt.ChatOptions;

class ChangeRequestExtractorTest {

    private final StubChatModel model = new StubChatModel();
    private final ChangeRequestExtractor extractor = new ChangeRequestExtractor(ConversationFixtures.llm(model),
            ConversationFixtures.prompts(), ChatOptions.builder().temperature(0.0).build());
    private final ChatContext ctx = ConversationFixtures.context();

    @Test
    void reduceBudgetToThreeMillion() {
        model.reply("""
                {"understood":true,"scope":"TRIP","dayIndex":null,"newBudgetVnd":3000000,"addPreferences":[],
                 "removePreferences":[],"preferIndoor":null,"excludePlaceIds":[],"instruction":"Giảm ngân sách còn 3 triệu"}""");

        ChangeRequest c = extractor.extract("Giảm còn 3 triệu", ctx).orElseThrow();
        TripRequest after = c.applyTo(ctx.request());

        assertThat(c.isDayScoped()).isFalse();
        assertThat(after.budgetVnd()).isEqualTo(3_000_000);
        assertThat(ctx.request().budgetVnd()).isEqualTo(6_000_000);   // immutable: original unchanged
        assertThat(after.destination()).isEqualTo(ctx.request().destination());
        assertThat(after.childrenAges()).isEqualTo(ctx.request().childrenAges());
        assertThat(c.summary(ctx.request(), List.of())).isEqualTo("Ngân sách 6.000.000đ → 3.000.000đ");
        assertThat(model.prompts().getFirst().getContents()).contains("THEME-PARK").contains("<plan>");
    }

    @Test
    void day2Indoor() {
        model.reply("""
                {"understood":true,"scope":"DAY","dayIndex":2,"newBudgetVnd":null,"addPreferences":[],
                 "removePreferences":[],"preferIndoor":true,"excludePlaceIds":[],"instruction":"Ngày 2 hoạt động trong nhà"}""");

        ChangeRequest c = extractor.extract("Ngày 2 trời mưa, đổi sang trong nhà", ctx).orElseThrow();

        assertThat(c.isDayScoped()).isTrue();
        assertThat(c.dayIndex()).isEqualTo(2);
        assertThat(c.wantsIndoor()).isTrue();
        assertThat(c.applyTo(ctx.request())).isEqualTo(ctx.request());   // constraints unchanged, only day 2 replanned
        assertThat(c.summary(ctx.request(), List.of())).isEqualTo("Ngày 2 được lập lại với hoạt động trong nhà");
    }

    @Test
    void removeBaNa() {
        model.reply("""
                {"understood":true,"scope":"TRIP","dayIndex":null,"newBudgetVnd":null,"addPreferences":[],
                 "removePreferences":["THEME-PARK"],"preferIndoor":null,
                 "excludePlaceIds":["danang-way-359114064","hanoi-way-37625751"],"instruction":"Bỏ Bà Nà"}""");

        ChangeRequest c = extractor.extract("Bỏ Bà Nà", ctx).orElseThrow();

        assertThat(c.excludePlaceIds()).containsExactly(Personas.BA_NA);   // id not in the plan is dropped
        assertThat(c.applyTo(ctx.request()).preferences()).containsExactly(PlaceTag.BEACH);
        assertThat(c.summary(ctx.request(), List.of("Sun World Bà Nà Hills")))
                .isEqualTo("Bỏ Sun World Bà Nà Hills; Bỏ sở thích [THEME-PARK]");
    }

    @Test
    void notUnderstoodOrNothingToChangeIsEmpty() {
        model.reply("""
                {"understood":false,"scope":"TRIP","excludePlaceIds":[],"instruction":""}""",
                """
                {"understood":true,"scope":"DAY","dayIndex":9,"newBudgetVnd":-5,"excludePlaceIds":["made-up"],
                 "addPreferences":[],"removePreferences":[],"instruction":"?"}""",
                "not json");
        assertThat(extractor.extract("hmm", ctx)).isEmpty();
        assertThat(extractor.extract("ngày 9 giảm âm tiền", ctx)).isEmpty();   // everything sanitised away
        assertThat(extractor.extract("???", ctx)).isEqualTo(Optional.empty());
    }

    @Test
    void preferencesStayWithinTheFormListAndMaxSix() {
        ChangeRequest c = new ChangeRequest(true, ChangeRequest.Scope.TRIP, null, null,
                List.of(PlaceTag.SPA, PlaceTag.NATURE, PlaceTag.CULTURE, PlaceTag.HISTORY, PlaceTag.MUSEUM, PlaceTag.FOOD),
                List.of(), null, List.of(), "x");
        ChangeRequest clean = ChangeRequestExtractor.sanitize(c, ctx.request(), ctx.plan());
        assertThat(clean.addPreferences()).doesNotContain(PlaceTag.SPA);
        assertThat(clean.applyTo(ctx.request()).preferences()).hasSize(6);
    }
}
