package dev.travelpilot.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.travelpilot.common.domain.Destination;
import dev.travelpilot.common.domain.PlaceTag;
import dev.travelpilot.common.domain.PlaceType;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class CatalogReaderTest {

    private final CatalogReader reader = new CatalogReader();

    @Test
    void readsAllThreeCatalogs() {
        List<Place> places = reader.readAll();

        Map<Destination, Long> perDestination =
                places.stream().collect(Collectors.groupingBy(Place::destination, Collectors.counting()));
        assertThat(places).hasSize(101);
        assertThat(perDestination).containsEntry(Destination.HANOI, 45L)
                .containsEntry(Destination.DANANG, 34L)
                .containsEntry(Destination.QUANGNINH, 22L);
        assertThat(places).allMatch(Place::curated);
    }

    @Test
    void mapsFieldsOfBaNaHills() {
        Map<String, Place> byId = reader.readAll().stream()
                .collect(Collectors.toMap(Place::id, Function.identity()));
        Place baNa = byId.get("danang-way-359114064");

        assertThat(baNa.name()).isEqualTo("Sun World Bà Nà Hills");
        assertThat(baNa.type()).isEqualTo(PlaceType.ACTIVITY);
        assertThat(baNa.hasTag(PlaceTag.THEME_PARK)).isTrue();
        assertThat(baNa.estCostVnd()).isEqualTo(1_000_000);
        assertThat(baNa.childCostVnd()).isEqualTo(800_000);
        assertThat(baNa.dayTrip()).isTrue();
        assertThat(baNa.verifyAt()).isNotNull();
    }

    @Test
    void missingRequiredFieldNamesFileAndElement() {
        assertThatThrownBy(() -> reader.readAll("classpath:fixtures/catalog/catalog_missing_field.json"))
                .isInstanceOf(CatalogReader.CatalogException.class)
                .hasMessageContaining("catalog_missing_field.json")
                .hasMessageContaining("element 0")
                .hasMessageContaining("durationMin");
    }

    @Test
    void unknownTagIsRejected() {
        assertThatThrownBy(() -> reader.readAll("classpath:fixtures/catalog/catalog_unknown_tag.json"))
                .isInstanceOf(CatalogReader.CatalogException.class)
                .hasMessageContaining("catalog_unknown_tag.json");
    }

    @Test
    void noFilesIsAnError() {
        assertThatThrownBy(() -> reader.readAll("classpath:fixtures/catalog/nothing_*.json"))
                .hasMessageContaining("No catalog files found");
    }
}
