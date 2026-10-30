package dev.travelpilot.recommendation;

import dev.travelpilot.trip.Warning;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/** Ranked candidates the planner may choose from (best first), plus why others were dropped. */
public record CandidateSet(
        List<ScoredPlace> activities,
        List<ScoredPlace> food,
        List<Warning> warnings,
        Map<String, FilterReason> rejected) {

    public CandidateSet {
        activities = List.copyOf(activities);
        food = List.copyOf(food);
        warnings = List.copyOf(warnings);
        rejected = Map.copyOf(rejected);
    }

    public Stream<ScoredPlace> all() {
        return Stream.concat(activities.stream(), food.stream());
    }

    public Map<String, ScoredPlace> byId() {
        Map<String, ScoredPlace> map = new LinkedHashMap<>();
        all().forEach(c -> map.put(c.id(), c));
        return map;
    }

    public Optional<ScoredPlace> find(String placeId) {
        return all().filter(c -> c.id().equals(placeId)).findFirst();
    }

    public boolean isEmpty() {
        return activities.isEmpty() && food.isEmpty();
    }
}
