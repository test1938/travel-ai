package dev.travelpilot.conversation;

import static org.assertj.core.api.Assertions.assertThat;

import dev.travelpilot.planner.PlannerFixtures;
import dev.travelpilot.support.IntegrationTest;
import dev.travelpilot.trip.TripRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Needs Docker. */
@IntegrationTest
class ConversationRepositoryTest {

    @Autowired
    TripRepository trips;

    @Autowired
    ConversationService conversations;

    @Test
    void storesHistoryAndReturnsABoundedWindow() {
        UUID trip = UUID.randomUUID();
        trips.insertTrip(trip, "guest-1", PlannerFixtures.family(), 1);

        for (int i = 1; i <= 4; i++) {
            conversations.addUserMessage(trip, "câu hỏi " + i);
            conversations.addAssistantMessage(trip, "trả lời " + i, Intent.ASK_QUESTION, 1);
        }

        assertThat(conversations.history(trip)).hasSize(8).first().extracting(ChatMessage::content)
                .isEqualTo("câu hỏi 1");
        assertThat(conversations.window(trip)).hasSize(ConversationService.WINDOW)
                .extracting(ChatMessage::content)
                .containsExactly("câu hỏi 2", "trả lời 2", "câu hỏi 3", "trả lời 3", "câu hỏi 4", "trả lời 4");
        assertThat(conversations.window(trip).getLast().intent()).isEqualTo(Intent.ASK_QUESTION);
    }
}
