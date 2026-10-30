package dev.travelpilot.catalog;

import static dev.travelpilot.catalog.OpeningHours.Status.CLOSED;
import static dev.travelpilot.catalog.OpeningHours.Status.OPEN;
import static dev.travelpilot.catalog.OpeningHours.Status.UNKNOWN;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class OpeningHoursTest {

    // 2026-10-19 is a Monday.
    static final LocalDate MON = LocalDate.of(2026, 10, 19);
    static final LocalDate TUE = MON.plusDays(1);
    static final LocalDate FRI = MON.plusDays(4);
    static final LocalDate SAT = MON.plusDays(5);

    private static final Map<String, Place> CATALOG = new CatalogReader().readAll().stream()
            .collect(Collectors.toMap(Place::id, Function.identity()));

    private static OpeningHours hoursOf(String placeId) {
        return OpeningHours.parse(CATALOG.get(placeId).openingHours());
    }

    @Test
    void everyCatalogStringIsUnderstood() {
        assertThat(CATALOG.values())
                .allSatisfy(p -> assertThat(OpeningHours.parse(p.openingHours()).isKnown())
                        .as("%s: %s", p.id(), p.openingHours()).isTrue());
    }

    @Test
    void hoChiMinhMausoleumIsClosedDuringRenovationAndOpenAfter() {
        OpeningHours lang = hoursOf("hanoi-way-37625751");
        assertThat(lang.isOpen(LocalDate.of(2026, 10, 20))).isEqualTo(CLOSED);   // Tue inside "Sep 04-Nov 02 off"
        assertThat(lang.isOpen(LocalDate.of(2026, 11, 4))).isEqualTo(OPEN);      // Wed, Nov-Mar Tu-Th 08:00-11:00
        assertThat(lang.isOpenAt(LocalDate.of(2026, 11, 4), LocalTime.of(9, 0))).isEqualTo(OPEN);
        assertThat(lang.isOpenAt(LocalDate.of(2026, 11, 4), LocalTime.of(11, 30))).isEqualTo(CLOSED);
        assertThat(lang.isOpen(LocalDate.of(2026, 11, 9))).isEqualTo(CLOSED);    // Monday
        assertThat(lang.isOpen(LocalDate.of(2027, 4, 17))).isEqualTo(OPEN);      // Sat, Apr-Oct Sa,Su,PH
    }

    @Test
    void airForceMuseumIsClosedOnFriday() {
        OpeningHours museum = hoursOf("hanoi-way-1219524639");   // "Mo-Su 08:00-11:00,13:00-16:00; Fr off"
        assertThat(museum.isOpen(FRI)).isEqualTo(CLOSED);
        assertThat(museum.isOpenAt(TUE, LocalTime.of(10, 0))).isEqualTo(OPEN);
        assertThat(museum.isOpenAt(TUE, LocalTime.of(12, 0))).isEqualTo(CLOSED);   // lunch break
    }

    @Test
    void rangeRunningPastMidnightCoversEarlyHoursOfNextDay() {
        OpeningHours bar = OpeningHours.parse("Mo-Su 18:00-02:00");
        assertThat(bar.isOpenAt(TUE, LocalTime.of(1, 0))).isEqualTo(OPEN);
        assertThat(bar.isOpenAt(TUE, LocalTime.of(3, 0))).isEqualTo(CLOSED);
        assertThat(bar.isOpen(TUE, LocalTime.of(20, 0), LocalTime.of(23, 30))).isEqualTo(OPEN);
        assertThat(OpeningHours.parse("Mo-Su 19:00-24:00").isOpenAt(TUE, LocalTime.of(23, 59))).isEqualTo(OPEN);
    }

    @Test
    void weekdaySelectorsAndLists() {
        OpeningHours museum = OpeningHours.parse("Tu-Th,Sa,Su 08:00-12:00,14:00-16:30");
        assertThat(museum.isOpen(MON)).isEqualTo(CLOSED);
        assertThat(museum.isOpen(FRI)).isEqualTo(CLOSED);
        assertThat(museum.isOpen(SAT)).isEqualTo(OPEN);
        assertThat(museum.isOpen(TUE, LocalTime.of(14, 0), LocalTime.of(16, 0))).isEqualTo(OPEN);
        assertThat(museum.isOpen(TUE, LocalTime.of(11, 0), LocalTime.of(13, 0))).isEqualTo(CLOSED);
    }

    @Test
    void laterRuleReplacesEarlierOneForTheSameDay() {
        OpeningHours market = OpeningHours.parse("Mo-Th 06:00-18:00; Fr-Su 06:00-22:30");
        assertThat(market.isOpenAt(MON, LocalTime.of(20, 0))).isEqualTo(CLOSED);
        assertThat(market.isOpenAt(SAT, LocalTime.of(20, 0))).isEqualTo(OPEN);
    }

    @Test
    void commaSpaceRulesAddUp() {
        OpeningHours buffet = OpeningHours.parse("Mo-Su 11:00-14:00, Mo-Su 17:30-21:30");
        assertThat(buffet.isOpenAt(MON, LocalTime.of(12, 0))).isEqualTo(OPEN);
        assertThat(buffet.isOpenAt(MON, LocalTime.of(19, 0))).isEqualTo(OPEN);
        assertThat(buffet.isOpenAt(MON, LocalTime.of(15, 0))).isEqualTo(CLOSED);
        assertThat(OpeningHours.parse("Mo-Su 06:00-13:30, 15:00-23:00").isOpenAt(MON, LocalTime.of(16, 0)))
                .isEqualTo(OPEN);
    }

    @Test
    void monthAndDateRangesWrapAroundTheYear() {
        OpeningHours peninsula = OpeningHours.parse("Mar-Sep 07:30-18:30; Oct-Feb 07:30-17:30");
        assertThat(peninsula.isOpenAt(LocalDate.of(2027, 1, 10), LocalTime.of(18, 0))).isEqualTo(CLOSED);
        assertThat(peninsula.isOpenAt(LocalDate.of(2027, 6, 10), LocalTime.of(18, 0))).isEqualTo(OPEN);

        OpeningHours cruise = OpeningHours.parse("Apr 16-Oct 31 07:30-17:30; Nov 01-Apr 15 07:30-17:00");
        assertThat(cruise.isOpenAt(LocalDate.of(2027, 2, 1), LocalTime.of(17, 15))).isEqualTo(CLOSED);
        assertThat(cruise.isOpenAt(LocalDate.of(2027, 5, 1), LocalTime.of(17, 15))).isEqualTo(OPEN);
    }

    @Test
    void specialValues() {
        assertThat(OpeningHours.parse("24/7").isOpenAt(MON, LocalTime.of(3, 0))).isEqualTo(OPEN);
        assertThat(OpeningHours.parse("08:00-17:00").isOpen(SAT)).isEqualTo(OPEN);
        assertThat(OpeningHours.parse("sunrise-sunset").isOpenAt(MON, LocalTime.of(20, 0))).isEqualTo(CLOSED);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "open daily", "Mo-Su 25:00-26:00", "Xy 08:00-10:00", "off"})
    void unparseableStringsAreUnknownNotExceptions(String value) {
        OpeningHours hours = OpeningHours.parse(value);
        assertThat(hours.isKnown()).isFalse();
        assertThat(hours.isOpen(MON)).isEqualTo(UNKNOWN);
        assertThat(hours.isOpenAt(MON, LocalTime.NOON)).isEqualTo(UNKNOWN);
    }

    @Test
    void nullIsUnknown() {
        assertThat(OpeningHours.parse(null).isOpen(MON)).isEqualTo(UNKNOWN);
    }
}
