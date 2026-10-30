package dev.travelpilot.integration.inventory;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class MockInventoryProviderTest {

    private static final String BA_NA = "danang-way-359114064";
    private static final LocalDate DAY = LocalDate.of(2026, 11, 12);

    private final MockInventoryProvider inventory = new MockInventoryProvider();

    @Test
    void sameQuestionSameAnswer() {
        assertThat(inventory.check(BA_NA, DAY, 3)).isEqualTo(inventory.check(BA_NA, DAY, 3));
        assertThat(new MockInventoryProvider().check(BA_NA, DAY, 3)).isEqualTo(inventory.check(BA_NA, DAY, 3));
    }

    @Test
    void alwaysMarkedAsDemo() {
        Availability a = inventory.check(BA_NA, DAY, 2);
        assertThat(a.demo()).isTrue();
        assertThat(a.note()).contains("mô phỏng");
    }

    @Test
    void availabilityDependsOnGroupSize() {
        int remaining = MockInventoryProvider.remaining(BA_NA, DAY);
        assertThat(inventory.check(BA_NA, DAY, remaining).available()).isEqualTo(remaining > 0);
        assertThat(inventory.check(BA_NA, DAY, remaining + 1).available()).isFalse();
    }

    @Test
    void variesAcrossDatesWithSomeSoldOut() {
        var values = IntStream.range(0, 60).map(i -> MockInventoryProvider.remaining(BA_NA, DAY.plusDays(i)))
                .boxed().toList();
        assertThat(values).allMatch(v -> v >= 0 && v <= MockInventoryProvider.MAX_REMAINING);
        assertThat(values).contains(0);
        assertThat(values.stream().distinct().count()).isGreaterThan(10);
    }
}
