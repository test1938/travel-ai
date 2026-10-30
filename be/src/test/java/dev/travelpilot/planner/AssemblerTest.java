package dev.travelpilot.planner;

import static org.assertj.core.api.Assertions.assertThat;

import dev.travelpilot.common.domain.PlaceType;
import dev.travelpilot.common.domain.Slot;
import dev.travelpilot.planner.TripPlanDraft.DraftDay;
import dev.travelpilot.planner.TripPlanDraft.DraftItem;
import dev.travelpilot.support.Personas;
import dev.travelpilot.trip.TripPlan;
import dev.travelpilot.trip.TripPlan.PlanItem;
import dev.travelpilot.trip.TripPlan.PlanMeta;
import dev.travelpilot.trip.Warning;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AssemblerTest {

    private final Assembler assembler = new Assembler();
    private final PlanContext ctx = PlannerFixtures.context();
    private final PlanMeta meta = new PlanMeta("planner-v1", "stub", false, 0, false, 10);

    private TripPlan assemble(TripPlanDraft draft) {
        return assembler.assemble(draft, ctx, List.of(), List.of(), meta);
    }

    @Test
    void timesComeFromTheSharedScheduleAndNeverOverlap() {
        TripPlan plan = assemble(PlannerFixtures.validDraft());

        assertThat(plan.days()).hasSize(3);
        assertThat(plan.days().get(0).items()).extracting(i -> i.start().toString())
                .containsExactly("08:30", "12:00", "13:30", "18:30");
        PlanItem baNa = plan.days().get(2).items().getFirst();
        assertThat(baNa.placeId()).isEqualTo(Personas.BA_NA);
        assertThat(baNa.start().toString()).isEqualTo("08:00");
        assertThat(baNa.end().toString()).isEqualTo("16:00");
        for (TripPlan.DayPlan day : plan.days()) {
            for (int i = 1; i < day.items().size(); i++) {
                assertThat(day.items().get(i).start()).isAfterOrEqualTo(day.items().get(i - 1).end());
            }
        }
        assertThat(plan.days().get(1).date()).isEqualTo(Personas.START.plusDays(1));
        assertThat(plan.days().get(1).weather().rainy()).isTrue();
    }

    @Test
    void pricesAndTotalsComeFromTheCatalog() {
        TripPlan plan = assemble(PlannerFixtures.validDraft());

        PlanItem baNa = plan.days().get(2).items().getFirst();
        assertThat(baNa.costVnd()).isEqualTo(2_800_000);
        assertThat(baNa.verifyAt()).isNotNull();
        assertThat(plan.budget().totalVnd()).isEqualTo(PlannerFixtures.VALID_DRAFT_TOTAL);
        assertThat(plan.budget().limitVnd()).isEqualTo(6_000_000);
        assertThat(plan.budget().overBudget()).isFalse();
        assertThat(plan.budget().byType().get(PlaceType.ACTIVITY) + plan.budget().byType().get(PlaceType.FOOD))
                .isEqualTo(PlannerFixtures.VALID_DRAFT_TOTAL);
        PlanItem lunch = plan.days().get(0).items().get(1);
        assertThat(lunch.type()).isEqualTo(PlaceType.FOOD);
        assertThat(lunch.costEstimated()).isTrue();   // restaurant prices are estimates
        assertThat(lunch.score()).isNotNull();
    }

    @Test
    void sourcesAreOnlyKnownEvidenceInOrder() {
        TripPlan plan = assemble(PlannerFixtures.validDraft());
        assertThat(plan.sources()).extracting(s -> s.id()).containsExactly("ev-1");
        assertThat(plan.days().get(2).items().getFirst().sourceIds()).containsExactly("ev-1");

        TripPlan bad = assemble(PlannerFixtures.withDay(1,
                new DraftItem(PlannerFixtures.MY_KHE, Slot.MORNING, "r", List.of("ev-99", "ev-1", "ev-1"))));
        assertThat(bad.days().getFirst().items().getFirst().sourceIds()).containsExactly("ev-1");
    }

    @Test
    void reasonsAreFixedWhenEmptyOrTooLong() {
        TripPlan plan = assemble(PlannerFixtures.withDay(1,
                new DraftItem(PlannerFixtures.MY_KHE, Slot.MORNING, "  ", List.of()),
                new DraftItem(PlannerFixtures.AEON, Slot.AFTERNOON, "x".repeat(400), List.of())));
        assertThat(plan.days().getFirst().items().get(0).reason()).isEqualTo(Assembler.DEFAULT_REASON);
        assertThat(plan.days().getFirst().items().get(1).reason()).hasSize(PlanRules.MAX_REASON_CHARS);
    }

    @Test
    void warningsIncludeSoftViolationsDayTripLunchAndDisclaimerLast() {
        Violation rain = Violation.soft("RAIN_OUTDOOR", "Dự báo mưa", 2, PlannerFixtures.MY_KHE);
        Violation silent = Violation.soft("BAD_SOURCE", "fixed", 1, PlannerFixtures.MY_KHE);
        TripPlan plan = assembler.assemble(PlannerFixtures.validDraft(), ctx, List.of(rain, silent, rain),
                List.of(Warning.of("WEATHER_UNAVAILABLE", "x")), meta);

        assertThat(plan.warnings()).extracting(Warning::code)
                .containsExactly("WEATHER_UNAVAILABLE", "DAYTRIP_LUNCH", "RAIN_OUTDOOR", "PRICE_ESTIMATE_DISCLAIMER");
        assertThat(plan.warnings().get(1).dayIndex()).isEqualTo(3);
    }

    @Test
    void overBudgetPlanGetsAWarning() {
        PlanContext poor = PlannerFixtures.context(PlannerFixtures.family().withBudget(1_000_000));
        TripPlanDraft draft = new TripPlanDraft("", null, List.of(
                new DraftDay(1, List.of(new DraftItem("danang-node-5485483822", Slot.EVENING, "r", List.of()))),
                new DraftDay(2, List.of()), new DraftDay(3, List.of())));
        // Seafood dinner (1,300,000) is not a candidate under this budget: unknown places are skipped.
        TripPlan plan = assembler.assemble(draft, poor, List.of(), List.of(), meta);
        assertThat(plan.days().getFirst().items()).isEmpty();
        assertThat(plan.title()).isEqualTo("Lịch trình 3 ngày ở Đà Nẵng");

        TripPlan over = assembler.assemble(PlannerFixtures.validDraft(),
                new PlanContext(PlannerFixtures.family().withBudget(5_000_000), ctx.candidates(), ctx.forecast(),
                        ctx.evidence(), Map.of(), Map.of()),
                List.of(), List.of(), meta);
        assertThat(over.budget().overBudget()).isTrue();
        assertThat(over.warnings()).extracting(Warning::code).contains("OVER_BUDGET");
    }
}
