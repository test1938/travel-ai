package dev.travelpilot.common.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

class PlaceTagTest {

    private final JsonMapper mapper = JsonMapper.builder().build();

    @ParameterizedTest
    @EnumSource(PlaceTag.class)
    void codeRoundTrips(PlaceTag tag) {
        assertThat(PlaceTag.fromCode(tag.code())).isEqualTo(tag);
    }

    @Test
    void hyphenatedCodesMatchTheDataset() {
        assertThat(PlaceTag.THEME_PARK.code()).isEqualTo("THEME-PARK");
        assertThat(PlaceTag.CABLE_CAR.code()).isEqualTo("CABLE-CAR");
        assertThat(PlaceTag.STREET_FOOD.code()).isEqualTo("STREET-FOOD");
        assertThat(PlaceTag.fromCode("theme_park")).isEqualTo(PlaceTag.THEME_PARK);
    }

    @Test
    void jsonUsesHyphenatedCodes() {
        List<PlaceTag> tags = mapper.readValue("[\"THEME-PARK\",\"BEACH\"]", new TypeReference<>() {});
        assertThat(tags).containsExactly(PlaceTag.THEME_PARK, PlaceTag.BEACH);
        assertThat(mapper.writeValueAsString(List.of(PlaceTag.STREET_FOOD))).isEqualTo("[\"STREET-FOOD\"]");
    }

    @Test
    void unknownCodeIsRejected() {
        assertThatThrownBy(() -> PlaceTag.fromCode("ZOO")).hasMessageContaining("Unknown place tag: ZOO");
    }

    @Test
    void bestTimeAnyFitsEverySlot() {
        assertThat(BestTime.ANY.fits(Slot.EVENING)).isTrue();
        assertThat(BestTime.MORNING.fits(Slot.MORNING)).isTrue();
        assertThat(BestTime.MORNING.fits(Slot.EVENING)).isFalse();
    }
}
