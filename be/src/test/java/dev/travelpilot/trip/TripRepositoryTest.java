package dev.travelpilot.trip;

import static org.assertj.core.api.Assertions.assertThat;

import dev.travelpilot.planner.PlannerFixtures;
import dev.travelpilot.support.IntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Needs Docker: JSONB round trip through PostgreSQL. */
@IntegrationTest
class TripRepositoryTest {

    @Autowired
    TripRepository repository;

    @Test
    void createThenReadBackTheSamePlan() {
        UUID id = UUID.randomUUID();
        TripPlan plan = TripFixtures.familyResult().plan().withIdentity(id, 1);
        TripRequest req = PlannerFixtures.family();

        repository.insertTrip(id, "guest-1", req, 1);
        repository.insertVersion(id, 1, plan, req,
                new TripRepository.VersionFacts(null, "planner-v1", "stub", 1200, 300, 1500, false, false));

        assertThat(repository.findTrip(id)).get().satisfies(t -> {
            assertThat(t.guestId()).isEqualTo("guest-1");
            assertThat(t.request()).isEqualTo(req);
            assertThat(t.currentVersion()).isEqualTo(1);
        });
        assertThat(repository.findVersion(id, 1)).contains(plan);
        assertThat(repository.findVersion(id, 2)).isEmpty();
    }
}
