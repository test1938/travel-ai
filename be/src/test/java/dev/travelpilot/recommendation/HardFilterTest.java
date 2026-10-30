package dev.travelpilot.recommendation;

import static org.assertj.core.api.Assertions.assertThat;

import dev.travelpilot.catalog.Place;
import dev.travelpilot.common.domain.Destination;
import dev.travelpilot.common.domain.Pace;
import dev.travelpilot.support.Personas;
import dev.travelpilot.trip.TripRequest;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class HardFilterTest {

    private static TripRequest hanoi(LocalDate start, int days) {
        return new TripRequest(Destination.HANOI, start, days, 2, List.of(), 5_000_000, Set.of(), Pace.NORMAL,
                List.of());
    }

    @Test
    void placesOfOtherDestinationsAreRemoved() {
        HardFilter.Result r = HardFilter.apply(Personas.CATALOG, Personas.family(), HardFilter.Options.defaults());
        assertThat(r.kept()).allMatch(p -> p.destination() == Destination.DANANG);
        assertThat(r.rejected().get(Personas.MAUSOLEUM)).isEqualTo(FilterReason.WRONG_DESTINATION);
    }

    @Test
    void barIsRemovedForA6YearOld() {
        HardFilter.Result r = HardFilter.apply(Personas.placesOf(Destination.DANANG), Personas.family(),
                HardFilter.Options.defaults());
        assertThat(r.rejected()).containsEntry(Personas.CRAFTSMAN_BAR, FilterReason.CHILD_TOO_YOUNG);
        assertThat(r.kept()).extracting(Place::id).doesNotContain(Personas.CRAFTSMAN_BAR);
    }

    @Test
    void adultsOnlyTripKeepsTheBar() {
        TripRequest adults = new TripRequest(Destination.DANANG, Personas.START, 2, 2, List.of(), 6_000_000,
                Set.of(), Pace.NORMAL, List.of());
        assertThat(HardFilter.apply(Personas.placesOf(Destination.DANANG), adults, HardFilter.Options.defaults())
                .kept()).extracting(Place::id).contains(Personas.CRAFTSMAN_BAR);
    }

    @Test
    void itemCostingMoreThanHalfTheBudgetIsRemoved() {
        // Bà Nà costs 2,800,000 for the family: kept with 6,000,000, removed with 5,000,000 (limit 2,500,000).
        assertThat(HardFilter.apply(List.of(Personas.place(Personas.BA_NA)), Personas.family(),
                HardFilter.Options.defaults()).kept()).hasSize(1);
        HardFilter.Result tight = HardFilter.apply(List.of(Personas.place(Personas.BA_NA)),
                Personas.family().withBudget(5_000_000), HardFilter.Options.defaults());
        assertThat(tight.rejected()).containsEntry(Personas.BA_NA, FilterReason.TOO_EXPENSIVE);
        assertThat(HardFilter.apply(List.of(Personas.place(Personas.BA_NA)), Personas.family().withBudget(5_000_000),
                HardFilter.Options.defaults().withMaxCostShare(0.7)).kept()).hasSize(1);
    }

    @Test
    void mausoleumIsRemovedDuringRenovationButKeptAfter() {
        Place mausoleum = Personas.place(Personas.MAUSOLEUM);
        assertThat(HardFilter.apply(List.of(mausoleum), hanoi(LocalDate.of(2026, 10, 20), 1),
                HardFilter.Options.defaults()).rejected())
                .containsEntry(Personas.MAUSOLEUM, FilterReason.CLOSED_ON_TRIP_DATES);
        // A 3-day trip from Wed 2026-11-04 includes open days.
        assertThat(HardFilter.apply(List.of(mausoleum), hanoi(LocalDate.of(2026, 11, 4), 3),
                HardFilter.Options.defaults()).kept()).hasSize(1);
    }

    @Test
    void placeClosedOnlySomeDaysIsKept() {
        // Air-force museum is closed on Friday only; a Thu-Fri trip keeps it.
        Place museum = Personas.place("hanoi-way-1219524639");
        assertThat(HardFilter.apply(List.of(museum), hanoi(LocalDate.of(2026, 11, 12), 2),
                HardFilter.Options.defaults()).kept()).hasSize(1);
        assertThat(HardFilter.apply(List.of(museum), hanoi(LocalDate.of(2026, 11, 13), 1),
                HardFilter.Options.defaults()).rejected()).containsValue(FilterReason.CLOSED_ON_TRIP_DATES);
    }

    @Test
    void excludedIdsAreRemoved() {
        HardFilter.Result r = HardFilter.apply(List.of(Personas.place(Personas.BA_NA)), Personas.family(),
                HardFilter.Options.defaults().withExcludeIds(Set.of(Personas.BA_NA)));
        assertThat(r.rejected()).containsEntry(Personas.BA_NA, FilterReason.EXCLUDED_BY_USER);
    }

    @Test
    void indoorOnlyRemovesOutdoorPlaces() {
        HardFilter.Result r = HardFilter.apply(Personas.placesOf(Destination.DANANG), Personas.family(),
                new HardFilter.Options(0.5, Set.of(), true));
        assertThat(r.kept()).isNotEmpty().allMatch(Place::indoor);
        assertThat(r.rejected()).containsEntry(Personas.BA_NA, FilterReason.NOT_INDOOR);
    }
}
