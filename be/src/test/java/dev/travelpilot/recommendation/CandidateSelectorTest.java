package dev.travelpilot.recommendation;

import static org.assertj.core.api.Assertions.assertThat;

import dev.travelpilot.common.domain.Destination;
import dev.travelpilot.common.domain.Pace;
import dev.travelpilot.common.domain.PlaceTag;
import dev.travelpilot.integration.weather.Forecast;
import dev.travelpilot.support.Personas;
import dev.travelpilot.support.TestProps;
import dev.travelpilot.trip.TripRequest;
import dev.travelpilot.trip.Warning;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CandidateSelectorTest {

    private final CandidateSelector selector = new CandidateSelector(TestProps.defaults().scoring().weights());

    private CandidateSet select(TripRequest req) {
        return selector.select(Personas.CATALOG, req, Forecast.NONE, HardFilter.Options.defaults());
    }

    @Test
    void candidatesAreRankedBestFirstAndCapped() {
        CandidateSet set = select(Personas.family());

        assertThat(set.activities()).hasSizeLessThanOrEqualTo(12 + 2).isNotEmpty();   // cap + one pick per preference
        assertThat(set.food()).hasSizeLessThanOrEqualTo(9 + 2).isNotEmpty();
        assertThat(set.activities()).isSortedAccordingTo(
                (a, b) -> Double.compare(b.score().total(), a.score().total()));
        assertThat(set.all()).allMatch(c -> c.place().destination() == Destination.DANANG);
        assertThat(set.warnings()).isEmpty();
    }

    @Test
    void thinDataRelaxesAndWarnsButNeverReturnsEmpty() {
        TripRequest longQuangNinh = new TripRequest(Destination.QUANGNINH, Personas.START, 5, 2, List.of(3),
                3_000_000, Set.of(PlaceTag.NATURE), Pace.NORMAL, List.of());

        CandidateSet set = select(longQuangNinh);

        assertThat(set.activities()).isNotEmpty();
        assertThat(set.food()).isNotEmpty();
        assertThat(set.warnings()).extracting(Warning::code).contains("CANDIDATES_SHORT");
        assertThat(set.rejected()).containsValue(FilterReason.CHILD_TOO_YOUNG);   // onsen (12+), Yên Tử (6+)
    }

    @Test
    void tightBudgetRelaxesThePriceCap() {
        // 5 packed days need 20 activities but Quang Ninh has 12, so the selector relaxes. With 900,000 the
        // 50% cap (450k) drops the bay cruise (2 x 250k = 500k); the 70% cap (630k) brings it back.
        TripRequest tight = new TripRequest(Destination.QUANGNINH, Personas.START, 5, 2, List.of(), 900_000,
                Set.of(), Pace.PACKED, List.of());

        CandidateSet set = select(tight);

        assertThat(set.warnings()).extracting(Warning::code).contains("CANDIDATES_RELAXED", "CANDIDATES_SHORT");
        assertThat(set.activities()).extracting(ScoredPlace::id).contains("quangninh-node-2707025319");
    }

    @Test
    void noRelaxationWhenEnoughCheapPlacesExist() {
        TripRequest twoDays = new TripRequest(Destination.QUANGNINH, Personas.START, 2, 2, List.of(), 900_000,
                Set.of(), Pace.NORMAL, List.of());
        CandidateSet set = select(twoDays);
        assertThat(set.warnings()).isEmpty();
        assertThat(set.rejected()).containsEntry("quangninh-node-2707025319", FilterReason.TOO_EXPENSIVE);
    }

    @Test
    void lookupById() {
        CandidateSet set = select(Personas.family());
        String first = set.activities().getFirst().id();
        assertThat(set.find(first)).isPresent();
        assertThat(set.byId()).containsKey(first);
        assertThat(set.find("nope")).isEmpty();
        assertThat(set.isEmpty()).isFalse();
    }
}
