package dev.travelpilot.planner;

import static dev.travelpilot.planner.PlannerFixtures.AEON;
import static dev.travelpilot.planner.PlannerFixtures.BANH_XEO;
import static dev.travelpilot.planner.PlannerFixtures.CHAM_MUSEUM;
import static dev.travelpilot.planner.PlannerFixtures.DANANG_MUSEUM;
import static dev.travelpilot.planner.PlannerFixtures.DRAGON_BRIDGE;
import static dev.travelpilot.planner.PlannerFixtures.MI_QUANG_1A;
import static dev.travelpilot.planner.PlannerFixtures.MY_KHE;
import static dev.travelpilot.planner.PlannerFixtures.item;
import static dev.travelpilot.planner.PlannerFixtures.withDay;
import static org.assertj.core.api.Assertions.assertThat;

import dev.travelpilot.common.domain.Destination;
import dev.travelpilot.common.domain.Pace;
import dev.travelpilot.common.domain.Slot;
import dev.travelpilot.planner.TripPlanDraft.DraftDay;
import dev.travelpilot.planner.TripPlanDraft.DraftItem;
import dev.travelpilot.support.Personas;
import dev.travelpilot.trip.TripRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PlanValidatorTest {

    private final PlanValidator validator = new PlanValidator();
    private final PlanContext ctx = PlannerFixtures.context();

    private List<String> codes(TripPlanDraft draft) {
        return validator.validate(draft, ctx).violations().stream().map(Violation::code).toList();
    }

    private List<String> hardCodes(TripPlanDraft draft) {
        return validator.validate(draft, ctx).hard().stream().map(Violation::code).toList();
    }

    @Test
    void handCheckedDraftIsFullyValid() {
        PlanValidator.Report report = validator.validate(PlannerFixtures.validDraft(), ctx);
        assertThat(report.violations()).isEmpty();
        assertThat(report.isValid()).isTrue();
    }

    @Test
    void missingDraftIsSchemaInvalid() {
        assertThat(hardCodes(null)).containsExactly("SCHEMA_INVALID");
    }

    @Test
    void dayCount() {
        TripPlanDraft twoDays = new TripPlanDraft("t", "s", PlannerFixtures.validDraft().days().subList(0, 2));
        assertThat(hardCodes(twoDays)).contains("DAY_COUNT");
        List<DraftDay> shuffled = List.of(PlannerFixtures.validDraft().days().get(1),
                PlannerFixtures.validDraft().days().get(0), PlannerFixtures.validDraft().days().get(2));
        assertThat(hardCodes(new TripPlanDraft("t", "s", shuffled))).contains("DAY_COUNT");
    }

    @Test
    void missingSlotOrPlace() {
        assertThat(hardCodes(withDay(1, new DraftItem(MY_KHE, null, "r", List.of())))).contains("MISSING_FIELD");
        assertThat(hardCodes(withDay(1, item(" ", Slot.MORNING), item(MY_KHE, Slot.MORNING)))).contains("MISSING_FIELD");
    }

    @Test
    void unknownPlace() {
        // The Mausoleum is in Hanoi; an invented id is equally unknown.
        assertThat(hardCodes(withDay(1, item(Personas.MAUSOLEUM, Slot.MORNING)))).contains("UNKNOWN_PLACE");
        assertThat(hardCodes(withDay(1, item("danang-made-up", Slot.MORNING)))).contains("UNKNOWN_PLACE");
    }

    @Test
    void duplicateActivityIsHardRepeatedFoodIsSoft() {
        assertThat(hardCodes(withDay(2, item(MY_KHE, Slot.MORNING)))).contains("DUPLICATE_ACTIVITY");
        TripPlanDraft sameRestaurant = withDay(2, item(CHAM_MUSEUM, Slot.MORNING), item(MI_QUANG_1A, Slot.AFTERNOON));
        PlanValidator.Report r = validator.validate(sameRestaurant, ctx);
        assertThat(r.soft()).extracting(Violation::code).contains("FOOD_REPEATED");
        assertThat(r.hard()).isEmpty();
    }

    @Test
    void paceLimitAndEmptyDay() {
        TripPlanDraft threeActivities = withDay(2, item(CHAM_MUSEUM, Slot.MORNING), item(DANANG_MUSEUM, Slot.AFTERNOON),
                item("danang-way-759913076", Slot.AFTERNOON));
        assertThat(hardCodes(threeActivities)).contains("PACE_EXCEEDED");
        assertThat(hardCodes(withDay(2, item(BANH_XEO, Slot.EVENING)))).contains("EMPTY_DAY");
    }

    @Test
    void slotCapacityAndLongVisits() {
        // 180 + 180 minutes of activity in one afternoon (capacity 240).
        assertThat(hardCodes(withDay(1, item(MY_KHE, Slot.AFTERNOON), item(AEON, Slot.AFTERNOON))))
                .contains("SLOT_OVERFLOW");
        // Bà Nà in the morning needs an empty afternoon.
        assertThat(hardCodes(withDay(3, item(Personas.BA_NA, Slot.MORNING), item(MI_QUANG_1A, Slot.AFTERNOON))))
                .contains("SLOT_OVERFLOW");
        // ... and can never be in the evening.
        assertThat(hardCodes(withDay(3, item(Personas.BA_NA, Slot.EVENING)))).contains("SLOT_OVERFLOW");
        // ... nor share its slot with another activity.
        assertThat(hardCodes(withDay(3, item(Personas.BA_NA, Slot.MORNING), item(CHAM_MUSEUM, Slot.MORNING))))
                .contains("SLOT_OVERFLOW");
    }

    @Test
    void childAge() {
        TripRequest adults = new TripRequest(Destination.DANANG, Personas.START, 3, 2, List.of(), 6_000_000,
                PlannerFixtures.family().preferences(), Pace.RELAXED, List.of());
        PlanContext adultsCtx = PlannerFixtures.context(adults);
        TripPlanDraft withBar = withDay(1, item(MY_KHE, Slot.MORNING), item(Personas.CRAFTSMAN_BAR, Slot.EVENING));
        // The bar is a candidate for adults, so for the family it is both unknown and too adult.
        PlanContext familyWithBar = new PlanContext(ctx.request(), adultsCtx.candidates(), ctx.forecast(),
                ctx.evidence(), Map.of(), Map.of());
        assertThat(validator.validate(withBar, familyWithBar).hard()).extracting(Violation::code).contains("CHILD_AGE");
        assertThat(validator.validate(withBar, adultsCtx).hard()).extracting(Violation::code).doesNotContain("CHILD_AGE");
    }

    @Test
    void outdoorPlaceOnRainyDayIsAWarning() {
        TripPlanDraft beachInRain = withDay(2, item(MY_KHE, Slot.MORNING));
        TripPlanDraft fixed = new TripPlanDraft("t", "s", List.of(
                new DraftDay(1, List.of(item(AEON, Slot.MORNING))),
                beachInRain.days().get(1),
                PlannerFixtures.validDraft().days().get(2)));
        PlanValidator.Report r = validator.validate(fixed, ctx);
        assertThat(r.soft()).extracting(Violation::code).contains("RAIN_OUTDOOR");
        assertThat(r.hard()).isEmpty();
    }

    @Test
    void unknownSourceIsAWarning() {
        TripPlanDraft badSource = withDay(1, new DraftItem(MY_KHE, Slot.MORNING, "r", List.of("ev-99")));
        assertThat(validator.validate(badSource, ctx).soft()).extracting(Violation::code).containsExactly("BAD_SOURCE");
    }

    @Test
    void overBudgetNamesTheMostExpensiveItem() {
        // Two seafood dinners (1,300,000 each for the family) instead of 208,000 and 650,000:
        // 5,064,000 - 208,000 - 650,000 + 2 x 1,300,000 = 6,806,000 > 6,000,000.
        String seafood = "danang-node-5485483822";
        List<DraftDay> days = new ArrayList<>(PlannerFixtures.validDraft().days());
        days.set(0, new DraftDay(1, List.of(item(MY_KHE, Slot.MORNING), item(MI_QUANG_1A, Slot.AFTERNOON),
                item(AEON, Slot.AFTERNOON), item(seafood, Slot.EVENING))));
        days.set(1, new DraftDay(2, List.of(item(CHAM_MUSEUM, Slot.MORNING),
                item(PlannerFixtures.BUN_CHA_CA, Slot.AFTERNOON), item(DANANG_MUSEUM, Slot.AFTERNOON),
                item(seafood, Slot.EVENING))));

        List<Violation> v = validator.validate(new TripPlanDraft("t", "s", days), ctx).hard();

        Violation over = v.stream().filter(x -> x.code().equals("OVER_BUDGET")).findFirst().orElseThrow();
        assertThat(over.message()).contains("6.806.000đ", "6.000.000đ", "Sun World Bà Nà Hills");
        assertThat(over.placeId()).isEqualTo(Personas.BA_NA);
    }

    @Test
    void blankOrLongReasonIsAWarning() {
        assertThat(codes(withDay(1, new DraftItem(MY_KHE, Slot.MORNING, " ", List.of())))).contains("BAD_REASON");
        assertThat(codes(withDay(1, new DraftItem(MY_KHE, Slot.MORNING, "x".repeat(301), List.of()))))
                .contains("BAD_REASON");
    }

    @Test
    void closedAtScheduledTimeIsAWarning() {
        // The Cham museum closes at 17:00 ("Mo-Su 07:30-17:00"); in the evening slot it would run 18:30-20:00.
        DraftItem museumAtNight = item(CHAM_MUSEUM, Slot.EVENING);
        assertThat(codes(withDay(1, item(MY_KHE, Slot.MORNING), museumAtNight))).contains("CLOSED_AT_SLOT");
    }

    @Test
    void lockedDayMustStayIdentical() {
        DraftDay lockedDay1 = PlannerFixtures.validDraft().days().getFirst();
        PlanContext withLock = new PlanContext(ctx.request(), ctx.candidates(), ctx.forecast(), ctx.evidence(),
                Map.of(1, lockedDay1), Map.of());
        assertThat(validator.validate(PlannerFixtures.validDraft(), withLock).hard()).isEmpty();
        assertThat(validator.validate(withDay(1, item(AEON, Slot.MORNING)), withLock).hard())
                .extracting(Violation::code).contains("LOCKED_DAY_CHANGED");
    }

    @Test
    void mealsBelongToLunchAndDinner() {
        assertThat(codes(withDay(1, item(MY_KHE, Slot.MORNING), item(BANH_XEO, Slot.MORNING)))).contains("MEAL_SLOT");
        assertThat(codes(withDay(1, item(MY_KHE, Slot.MORNING), item(BANH_XEO, Slot.EVENING),
                item(MI_QUANG_1A, Slot.EVENING)))).contains("MEAL_SLOT");
    }

    @Test
    void scheduleNeverOverlapsAndPutsMealsFirst() {
        var timeline = DaySchedule.schedule(PlannerFixtures.validDraft().days().getFirst(), ctx.knownPlaces());
        assertThat(timeline).extracting(t -> t.place().id()).containsExactly(MY_KHE, MI_QUANG_1A, AEON, BANH_XEO);
        assertThat(timeline).extracting(t -> t.start().toString()).containsExactly("08:30", "12:00", "13:30", "18:30");
        for (int i = 1; i < timeline.size(); i++) {
            assertThat(timeline.get(i).startMin()).isGreaterThanOrEqualTo(timeline.get(i - 1).endMin());
        }
        var baNaDay = DaySchedule.schedule(PlannerFixtures.validDraft().days().get(2), ctx.knownPlaces());
        assertThat(baNaDay.getFirst().start().toString()).isEqualTo("08:00");
        assertThat(baNaDay.getFirst().end().toString()).isEqualTo("16:00");
        assertThat(baNaDay).extracting(t -> t.place().id()).containsExactly(Personas.BA_NA,
                PlannerFixtures.MI_QUANG_ECH, DRAGON_BRIDGE);
    }
}
