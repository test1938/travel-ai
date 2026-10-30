package dev.travelpilot.trip;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.travelpilot.common.domain.Destination;
import dev.travelpilot.common.domain.Pace;
import dev.travelpilot.common.domain.PlaceTag;
import dev.travelpilot.common.error.ValidationFailedException;
import dev.travelpilot.support.Personas;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class TripRequestTest {

    private static final Validator BEAN_VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();
    /** 2026-10-30 10:00 in Vietnam. */
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-30T03:00:00Z"), Destination.ZONE);
    private final TripRequestValidator validator = new TripRequestValidator(CLOCK);

    private static TripRequest req(LocalDate start, int days, int adults, List<Integer> kids, long budget,
            Set<PlaceTag> prefs) {
        return new TripRequest(Destination.DANANG, start, days, adults, kids, budget, prefs, Pace.RELAXED, List.of());
    }

    private static Set<String> invalidFields(TripRequest r) {
        return BEAN_VALIDATOR.validate(r).stream()
                .map(ConstraintViolation::getPropertyPath).map(Object::toString)
                .map(p -> p.replaceAll("\\[.*", ""))
                .collect(Collectors.toSet());
    }

    @Test
    void validPersonasPass() {
        assertThat(invalidFields(Personas.family())).isEmpty();
        validator.validate(Personas.family());
    }

    @Test
    void daysMustBe1To5() {
        assertThat(invalidFields(req(Personas.START, 0, 2, List.of(), 1_000_000, Set.of()))).contains("days");
        assertThat(invalidFields(req(Personas.START, 6, 2, List.of(), 1_000_000, Set.of()))).contains("days");
    }

    @Test
    void childAgesMustBe0To17() {
        assertThat(invalidFields(req(Personas.START, 2, 2, List.of(18), 1_000_000, Set.of()))).contains("childrenAges");
        assertThat(invalidFields(req(Personas.START, 2, 2, List.of(-1), 1_000_000, Set.of()))).contains("childrenAges");
        assertThat(invalidFields(req(Personas.START, 2, 2, List.of(0, 17), 1_000_000, Set.of()))).isEmpty();
        List<Integer> withNull = new ArrayList<>(Collections.singletonList(null));
        // null ages are rejected while constructing the record (Jackson turns this into a 400)
        assertThatThrownBy(() -> req(Personas.START, 2, 2, withNull, 1_000_000, Set.of()))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void budgetAndAdultsMustBePositive() {
        assertThat(invalidFields(req(Personas.START, 2, 2, List.of(), 0, Set.of()))).contains("budgetVnd");
        assertThat(invalidFields(req(Personas.START, 2, 0, List.of(), 1_000_000, Set.of()))).contains("adults");
    }

    @Test
    void startDateInThePastIsRejectedInVietnamTime() {
        TripRequest yesterday = req(LocalDate.of(2026, 10, 29), 2, 2, List.of(), 1_000_000, Set.of());
        assertThatThrownBy(() -> validator.validate(yesterday))
                .isInstanceOf(ValidationFailedException.class)
                .satisfies(e -> assertThat(((ValidationFailedException) e).errors()).containsKey("startDate"));
        validator.validate(req(LocalDate.of(2026, 10, 30), 2, 2, List.of(), 1_000_000, Set.of()));
    }

    @Test
    void preferencesOutsideTheFormListAreRejected() {
        TripRequest spa = req(Personas.START, 2, 2, List.of(), 1_000_000, Set.of(PlaceTag.SPA, PlaceTag.BEACH));
        assertThatThrownBy(() -> validator.validate(spa))
                .isInstanceOf(ValidationFailedException.class)
                .satisfies(e -> assertThat(((ValidationFailedException) e).errors().get("preferences")).contains("SPA"));
    }

    @Test
    void groupCostCountsOnlyChildrenOldEnough() {
        TripRequest family = Personas.family();
        assertThat(family.groupCost(Personas.place(Personas.BA_NA))).isEqualTo(2_800_000);   // 2 x 1M + 1 x 800k

        TripRequest withToddler = new TripRequest(Destination.DANANG, Personas.START, 1, 2, List.of(6, 1),
                1_000_000, Set.of(), Pace.RELAXED, List.of());
        // Yên Tử requires age 6: the 1-year-old is not counted (and HardFilter removes such places anyway).
        assertThat(withToddler.groupCost(Personas.place("quangninh-node-4886986961"))).isZero();
        assertThat(withToddler.youngestChildAge()).hasValue(1);
        assertThat(withToddler.dates()).containsExactly(Personas.START);
    }
}
