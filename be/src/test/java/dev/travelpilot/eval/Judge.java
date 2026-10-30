package dev.travelpilot.eval;

import dev.travelpilot.conversation.ChatService;
import dev.travelpilot.trip.TripPlan;
import dev.travelpilot.trip.TripRequest;

/** Scores what the fixed checks cannot: are reasons and answers grounded, and are they useful (1-5). */
public interface Judge {

    /** Score of one plan or answer; {@code null} fields mean the judge could not score. */
    record Score(Integer groundedness, Integer reasonQuality, String comment) {

        public static Score failed(String why) {
            return new Score(null, null, why);
        }

        public boolean ok() {
            return groundedness != null && reasonQuality != null;
        }
    }

    Score plan(TripRequest request, TripPlan plan);

    Score answer(String question, ChatService.ChatReply reply);
}
