package dev.travelpilot.trip;

import static org.assertj.core.api.Assertions.assertThat;

import dev.travelpilot.planner.PlannerFixtures;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/** Plans and requests are stored as JSONB: they must survive a JSON round trip unchanged. */
class TripJsonTest {

    private final JsonMapper json = JsonMapper.builder().build();

    @Test
    void planRoundTrips() {
        TripPlan plan = TripFixtures.familyResult().plan().withIdentity(UUID.randomUUID(), 1);

        String text = json.writeValueAsString(plan);
        TripPlan back = json.readValue(text, TripPlan.class);

        assertThat(back).isEqualTo(plan);
        assertThat(text).contains("\"start\":\"08:30\"").contains("\"costVnd\":2800000");
    }

    @Test
    void requestRoundTripsWithHyphenatedTags() {
        TripRequest req = PlannerFixtures.family();
        String text = json.writeValueAsString(req);
        assertThat(text).contains("THEME-PARK");
        assertThat(json.readValue(text, TripRequest.class)).isEqualTo(req);
    }
}
