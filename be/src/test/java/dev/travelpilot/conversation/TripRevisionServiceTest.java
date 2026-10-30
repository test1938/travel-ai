package dev.travelpilot.conversation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import dev.travelpilot.catalog.Place;
import dev.travelpilot.catalog.PlaceRepository;
import dev.travelpilot.common.domain.PlaceType;
import dev.travelpilot.planner.OfflinePlanning;
import dev.travelpilot.planner.PlannerFixtures;
import dev.travelpilot.support.Personas;
import dev.travelpilot.support.StubChatModel;
import dev.travelpilot.trip.TripPlan;
import dev.travelpilot.trip.TripPlan.DayPlan;
import dev.travelpilot.trip.TripPlan.PlanItem;
import dev.travelpilot.trip.TripRepository;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

class TripRevisionServiceTest {

    private final StubChatModel model = new StubChatModel();
    private final PlaceRepository places = OfflinePlanning.places();
    private final TripRepository trips = mock(TripRepository.class);
    private final TripRevisionService service = new TripRevisionService(OfflinePlanning.orchestrator(model, places),
            places, trips, noTransaction());
    private final TripPlan v1 = ConversationFixtures.familyPlan();

    static TransactionTemplate noTransaction() {
        return new TransactionTemplate(new AbstractPlatformTransactionManager() {
            @Override
            protected Object doGetTransaction() {
                return new Object();
            }

            @Override
            protected void doBegin(Object transaction, TransactionDefinition definition) {}

            @Override
            protected void doCommit(DefaultTransactionStatus status) {}

            @Override
            protected void doRollback(DefaultTransactionStatus status) {}
        });
    }

    private static ChangeRequest day2Indoor() {
        return new ChangeRequest(true, ChangeRequest.Scope.DAY, 2, null, List.of(), List.of(), true, List.of(),
                "Ngày 2 hoạt động trong nhà");
    }

    /** What the traveler sees of a day: place, slot, time and reason. */
    private static List<String> visible(DayPlan day) {
        return day.items().stream().map(i -> i.slot() + " " + i.start() + " " + i.placeId() + " " + i.reason()).toList();
    }

    @Test
    void scenario3OnlyDay2ChangesAndOtherDaysStayIdentical() {
        model.fail(new RuntimeException("offline"));   // greedy fallback: deterministic

        TripRevisionService.Revision r = service.revise(v1.tripId(), PlannerFixtures.family(), v1, day2Indoor());
        TripPlan v2 = r.plan();

        assertThat(v2.version()).isEqualTo(2);
        assertThat(visible(v2.days().get(0))).isEqualTo(visible(v1.days().get(0)));
        assertThat(visible(v2.days().get(2))).isEqualTo(visible(v1.days().get(2)));
        assertThat(v2.days().get(2).items().getFirst().sourceIds()).containsExactly("v1-ev-1");
        assertThat(v2.sources()).extracting(s -> s.id()).contains("v1-ev-1");
        List<Place> day2Activities = v2.days().get(1).items().stream()
                .filter(i -> i.type() == PlaceType.ACTIVITY).map(i -> Personas.place(i.placeId())).toList();
        assertThat(day2Activities).isNotEmpty().allMatch(Place::indoor);
        assertThat(v2.budget().totalVnd()).isEqualTo(
                v2.days().stream().flatMap(d -> d.items().stream()).mapToLong(PlanItem::costVnd).sum());
        assertThat(r.changeSummary()).isEqualTo("Ngày 2 được lập lại với hoạt động trong nhà");

        verify(trips).insertVersion(eq(v1.tripId()), eq(2), eq(v2), eq(PlannerFixtures.family()), any());
        verify(trips).updateCurrent(v1.tripId(), 2, PlannerFixtures.family());
    }

    @Test
    void scenario3WithTheModelUsesTheReplanPromptAndKeepsLockedDays() {
        model.reply(JsonMapper.builder().build().writeValueAsString(PlannerFixtures.validDraft()));

        TripPlan v2 = service.revise(v1.tripId(), PlannerFixtures.family(), v1, day2Indoor()).plan();

        assertThat(v2.meta().fallback()).isFalse();
        assertThat(v2.meta().promptVersion()).isEqualTo("replan-v1");
        String sent = model.prompts().getFirst().getContents();
        assertThat(sent).contains("You are re-planning part of an existing trip")
                .contains("Day 1 (keep exactly): MORNING " + PlannerFixtures.MY_KHE)
                .contains("<instruction>\nNgày 2 hoạt động trong nhà\n</instruction>");
        assertThat(sent).doesNotContain("Day 2 (keep exactly)");
        assertThat(visible(v2.days().get(0))).isEqualTo(visible(v1.days().get(0)));
    }

    @Test
    void scenario4LowerBudgetReplansTheWholeTripWithinBudget() {
        model.fail(new RuntimeException("offline"));
        ChangeRequest budget = new ChangeRequest(true, ChangeRequest.Scope.TRIP, null, 3_000_000L, List.of(),
                List.of(), null, List.of(), "Giảm ngân sách còn 3 triệu");

        TripRevisionService.Revision r = service.revise(v1.tripId(), PlannerFixtures.family(), v1, budget);

        assertThat(r.request().budgetVnd()).isEqualTo(3_000_000);
        assertThat(r.plan().budget().limitVnd()).isEqualTo(3_000_000);
        assertThat(r.plan().budget().totalVnd()).isLessThanOrEqualTo(3_000_000);
        assertThat(r.plan().days().stream().flatMap(d -> d.items().stream()).map(PlanItem::placeId))
                .doesNotContain(Personas.BA_NA);   // 2.8M for the family is now over half the budget
        assertThat(r.changeSummary()).isEqualTo("Ngân sách 6.000.000đ → 3.000.000đ");
        verify(trips).updateCurrent(v1.tripId(), 2, r.request());
    }

    @Test
    void removingAPlaceReplansOnlyTheDaysThatContainIt() {
        ChangeRequest dropBaNa = new ChangeRequest(true, ChangeRequest.Scope.TRIP, null, null, List.of(), List.of(),
                null, List.of(Personas.BA_NA), "Bỏ Bà Nà");
        assertThat(TripRevisionService.daysToReplan(v1, dropBaNa, PlannerFixtures.family())).containsExactly(3);
        assertThat(TripRevisionService.daysToReplan(v1, day2Indoor(), PlannerFixtures.family())).containsExactly(2);
        ChangeRequest indoorEverywhere = new ChangeRequest(true, ChangeRequest.Scope.TRIP, null, null, List.of(),
                List.of(), true, List.of(), "Trong nhà");
        assertThat(TripRevisionService.daysToReplan(v1, indoorEverywhere, PlannerFixtures.family()))
                .isEqualTo(Set.of(1, 2, 3));

        model.fail(new RuntimeException("offline"));
        TripPlan v2 = service.revise(v1.tripId(), PlannerFixtures.family(), v1, dropBaNa).plan();
        assertThat(v2.days().get(2).items()).extracting(PlanItem::placeId).doesNotContain(Personas.BA_NA);
        assertThat(visible(v2.days().get(0))).isEqualTo(visible(v1.days().get(0)));
        assertThat(visible(v2.days().get(1))).isEqualTo(visible(v1.days().get(1)));
    }

    @Test
    void mergerRecomputesBudgetAndKeepsTheDisclaimerLast() {
        TripPlan fresh = ConversationFixtures.familyPlan().withIdentity(v1.tripId(), 2);
        TripPlan merged = LockedDayMerger.merge(fresh, v1, Set.of(1, 3));
        assertThat(merged.budget().totalVnd()).isEqualTo(PlannerFixtures.VALID_DRAFT_TOTAL);
        assertThat(merged.warnings().getLast().code()).isEqualTo("PRICE_ESTIMATE_DISCLAIMER");
        assertThat(merged.sources()).extracting(s -> s.id()).contains("ev-1", "v1-ev-1");
    }
}
