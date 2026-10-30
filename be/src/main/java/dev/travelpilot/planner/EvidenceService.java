package dev.travelpilot.planner;

import dev.travelpilot.common.domain.PlaceTag;
import dev.travelpilot.knowledge.Evidence;
import dev.travelpilot.knowledge.Retriever;
import dev.travelpilot.recommendation.CandidateSet;
import dev.travelpilot.recommendation.ScoredPlace;
import dev.travelpilot.trip.TripRequest;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * Passages about the top candidates, numbered ev-1..n for the planner prompt (at most 2 per place, 12 total,
 * enforced by {@link Retriever#forPlaces}).
 */
@Service
public class EvidenceService {

    static final int TOP_ACTIVITIES = 10;
    static final int TOP_FOOD = 4;

    private final Retriever retriever;

    public EvidenceService(Retriever retriever) {
        this.retriever = retriever;
    }

    public List<PlanEvidence> forCandidates(TripRequest req, CandidateSet candidates) {
        Set<String> placeIds = new LinkedHashSet<>();
        candidates.activities().stream().limit(TOP_ACTIVITIES).map(ScoredPlace::id).forEach(placeIds::add);
        candidates.food().stream().limit(TOP_FOOD).map(ScoredPlace::id).forEach(placeIds::add);

        List<Evidence> found = retriever.forPlaces(req.destination(), placeIds, query(req));
        List<PlanEvidence> numbered = new ArrayList<>(found.size());
        for (int i = 0; i < found.size(); i++) {
            numbered.add(new PlanEvidence("ev-" + (i + 1), found.get(i)));
        }
        return numbered;
    }

    /** Query that favours passages about what this traveler cares about. */
    static String query(TripRequest req) {
        String who = req.hasChildren() ? "gia đình có trẻ em" : req.adults() == 2 ? "cặp đôi" : "nhóm bạn";
        String likes = req.preferences().stream().map(PlaceTag::code).sorted()
                .map(c -> c.toLowerCase().replace('-', ' ')).collect(Collectors.joining(", "));
        return "Gợi ý tham quan ở " + req.destination().displayName() + " cho " + who
                + (likes.isEmpty() ? "" : ", thích " + likes);
    }
}
