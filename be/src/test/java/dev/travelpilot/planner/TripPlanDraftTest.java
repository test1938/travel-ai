package dev.travelpilot.planner;

import static org.assertj.core.api.Assertions.assertThat;

import dev.travelpilot.common.domain.Slot;
import org.junit.jupiter.api.Test;
import org.springframework.ai.converter.BeanOutputConverter;

class TripPlanDraftTest {

    private final BeanOutputConverter<TripPlanDraft> converter = new BeanOutputConverter<>(TripPlanDraft.class);

    @Test
    void jsonSchemaCarriesFieldDescriptionsAndSlotEnum() {
        String schema = converter.getJsonSchema();
        assertThat(schema).contains("\"placeId\"", "\"slot\"", "\"sourceIds\"", "\"dayIndex\"")
                .contains("Candidate id copied exactly from the candidates table")
                .contains("MORNING", "AFTERNOON", "EVENING")
                .doesNotContain("costVnd", "price");
    }

    @Test
    void parsesModelOutputIncludingMarkdownFenceAndMissingLists() {
        String raw = """
                ```json
                {"title": "Đà Nẵng 3 ngày", "summary": "Biển và Bà Nà.",
                 "days": [{"dayIndex": 1, "items": [
                    {"placeId": "danang-way-359114064", "slot": "MORNING", "reason": "Bé thích công viên", "sourceIds": ["ev-1"]},
                    {"placeId": "danang-node-4430077491", "slot": "EVENING", "reason": "Mì Quảng đặc sản"}]}]}
                ```""";

        TripPlanDraft draft = converter.convert(raw);

        assertThat(draft.days()).hasSize(1);
        assertThat(draft.days().getFirst().items()).hasSize(2);
        assertThat(draft.days().getFirst().items().get(0).slot()).isEqualTo(Slot.MORNING);
        assertThat(draft.days().getFirst().items().get(1).sourceIds()).isEmpty();
    }
}
