package dev.travelpilot.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import dev.travelpilot.common.domain.Destination;
import dev.travelpilot.common.domain.PlaceTag;
import dev.travelpilot.common.domain.PlaceType;
import dev.travelpilot.support.IntegrationTest;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Runs against pgvector in Docker; the context seeds the catalog on startup (SeedLoader). */
@IntegrationTest
class PlaceRepositoryTest {

    static final String BA_NA = "danang-way-359114064";

    @Autowired
    PlaceRepository repository;

    @Autowired
    SeedLoader seedLoader;

    @Autowired
    CatalogReader reader;

    @Test
    void seedingTwiceKeepsOneRowPerPlace() {
        assertThat(repository.count()).isEqualTo(101);
        assertThat(seedLoader.seed()).isEqualTo(101);
        assertThat(repository.count()).isEqualTo(101);
    }

    @Test
    void rowsRoundTripExactly() {
        for (Place expected : reader.readAll()) {
            assertThat(repository.findById(expected.id())).contains(expected);
        }
    }

    @Test
    void filtersByDestinationTypeAndHyphenatedTag() {
        List<Place> themeParks = repository.find(
                new PlaceQuery(Destination.DANANG, PlaceType.ACTIVITY, PlaceTag.THEME_PARK, 0, 20));
        assertThat(themeParks).extracting(Place::id).contains(BA_NA);
        assertThat(themeParks).allMatch(p -> p.destination() == Destination.DANANG && p.hasTag(PlaceTag.THEME_PARK));
    }

    @Test
    void pagesAndCounts() {
        PlaceQuery firstPage = new PlaceQuery(Destination.HANOI, null, null, 0, 10);
        assertThat(repository.count(firstPage)).isEqualTo(45);
        assertThat(repository.find(firstPage)).hasSize(10);
        assertThat(repository.find(new PlaceQuery(Destination.HANOI, null, null, 4, 10))).hasSize(5);
    }

    @Test
    void findByIdsKeepsRequestOrderAndSkipsUnknown() {
        List<Place> places = repository.findByIds(List.of(BA_NA, "nope", "hanoi-way-37625751"));
        assertThat(places).extracting(Place::id).containsExactly(BA_NA, "hanoi-way-37625751");
    }
}
