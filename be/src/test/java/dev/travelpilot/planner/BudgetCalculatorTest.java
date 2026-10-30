package dev.travelpilot.planner;

import static org.assertj.core.api.Assertions.assertThat;

import dev.travelpilot.common.domain.BestTime;
import dev.travelpilot.common.domain.PlaceType;
import dev.travelpilot.common.domain.Slot;
import dev.travelpilot.support.Personas;
import dev.travelpilot.trip.TripPlan.Budget;
import dev.travelpilot.trip.TripPlan.PlanItem;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class BudgetCalculatorTest {

    private static PlanItem item(PlaceType type, long cost) {
        return new PlanItem("id", "n", type, Slot.MORNING, LocalTime.NOON, LocalTime.NOON, 60, cost, false, "", null,
                false, BestTime.ANY, "r", List.of(), null, 0, 0);
    }

    @Test
    void baNaForTwoAdultsAndASixYearOld() {
        assertThat(BudgetCalculator.itemCost(Personas.place(Personas.BA_NA), Personas.family())).isEqualTo(2_800_000);
    }

    @Test
    void mealIsPricedPerPerson() {
        // Mì Quảng 1A: 80,000 adult / 48,000 child -> 2 x 80k + 48k
        assertThat(BudgetCalculator.itemCost(Personas.place("danang-node-4430077491"), Personas.family()))
                .isEqualTo(208_000);
    }

    @Test
    void totalsByTypeAndOverBudgetFlag() {
        List<PlanItem> items = List.of(item(PlaceType.ACTIVITY, 2_800_000), item(PlaceType.ACTIVITY, 0),
                item(PlaceType.FOOD, 208_000), item(PlaceType.FOOD, 650_000));

        Budget ok = BudgetCalculator.total(items, 6_000_000);
        assertThat(ok.totalVnd()).isEqualTo(3_658_000);
        assertThat(ok.byType()).containsEntry(PlaceType.ACTIVITY, 2_800_000L).containsEntry(PlaceType.FOOD, 858_000L);
        assertThat(ok.overBudget()).isFalse();

        assertThat(BudgetCalculator.total(items, 3_000_000).overBudget()).isTrue();
        assertThat(BudgetCalculator.total(items, 3_658_000).overBudget()).isFalse();   // equal is within budget
    }

    @Test
    void emptyPlanCostsNothing() {
        Budget b = BudgetCalculator.total(List.of(), 1_000_000);
        assertThat(b.totalVnd()).isZero();
        assertThat(b.byType()).containsEntry(PlaceType.FOOD, 0L);
    }

    @Test
    void totalOfPlacesCountsRepeats() {
        var pho = Personas.place("danang-node-4430077491");
        assertThat(BudgetCalculator.totalOf(List.of(pho, pho), Personas.family())).isEqualTo(416_000);
    }
}
