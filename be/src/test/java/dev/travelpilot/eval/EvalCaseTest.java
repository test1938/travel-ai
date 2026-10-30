package dev.travelpilot.eval;

import static org.assertj.core.api.Assertions.assertThat;

import dev.travelpilot.catalog.CatalogReader;
import dev.travelpilot.catalog.Place;
import dev.travelpilot.eval.EvalCase.Category;
import dev.travelpilot.trip.TripRequest;
import dev.travelpilot.trip.TripRequestValidator;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** T8.01: be/eval/cases.json is valid (runs in the normal build, no model needed). */
class EvalCaseTest {

    private static final List<EvalCase> CASES = EvalCase.load();
    private static final Map<String, Place> CATALOG = new CatalogReader().readAll().stream()
            .collect(Collectors.toMap(Place::id, Function.identity()));

    @Test
    void twentyCasesInFiveGroups() {
        assertThat(CASES).hasSize(20);
        assertThat(CASES).extracting(EvalCase::id).doesNotHaveDuplicates().allMatch(id -> id.matches("[a-z0-9-]+"));
        Map<Category, Long> perCategory = CASES.stream()
                .collect(Collectors.groupingBy(EvalCase::category, Collectors.counting()));
        assertThat(perCategory).containsExactlyInAnyOrderEntriesOf(Map.of(
                Category.HAPPY, 6L, Category.HARD, 4L, Category.MODIFY, 4L, Category.ASK, 3L, Category.ATTACK, 3L));
    }

    @Test
    void requestsAreValidTripRequests() {
        Validator beans = Validation.buildDefaultValidatorFactory().getValidator();
        TripRequestValidator rules = new TripRequestValidator(Clock.systemUTC());
        for (EvalCase c : CASES) {
            TripRequest req = c.request().toRequest(LocalDate.now());
            assertThat(beans.validate(req)).as(c.id()).isEmpty();
            rules.validate(req);   // throws on business rule violations
            assertThat(c.description()).as(c.id()).isNotBlank();
            assertThat(c.expect()).as(c.id()).isNotNull();
        }
    }

    @Test
    void everyPlaceIdIsARealCatalogPlaceOfTheTripDestination() {
        for (EvalCase c : CASES) {
            List<String> ids = Stream.concat(c.expect().mustIncludeAnyOf().stream(),
                    c.expect().mustNotInclude().stream()).toList();
            for (String id : ids) {
                assertThat(CATALOG).as(c.id() + " " + id).containsKey(id);
                assertThat(CATALOG.get(id).destination()).as(c.id() + " " + id).isEqualTo(c.request().destination());
            }
        }
    }

    @Test
    void chatCasesHaveAMessageAndPlanCasesDoNot() {
        for (EvalCase c : CASES) {
            if (c.category().chats()) {
                assertThat(c.message()).as(c.id()).isNotBlank();
            } else {
                assertThat(c.message()).as(c.id()).isNull();
            }
            c.expect().lockedDays().forEach(d -> assertThat(d).as(c.id()).isBetween(1, c.request().days()));
        }
    }
}
