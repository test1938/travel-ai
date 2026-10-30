package dev.travelpilot.planner;

import dev.travelpilot.catalog.Place;
import dev.travelpilot.integration.weather.Forecast;
import dev.travelpilot.planner.TripPlanDraft.DraftDay;
import dev.travelpilot.recommendation.CandidateSet;
import dev.travelpilot.recommendation.ScoredPlace;
import dev.travelpilot.trip.TripRequest;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Everything one planning run needs. {@code lockedDays} (dayIndex -> day) are days that must stay unchanged
 * when re-planning part of a trip; their places may be outside the candidate set, so they are passed in
 * {@code lockedPlaces}. {@code instruction} is the traveler's change request when re-planning (else null).
 */
public record PlanContext(
        TripRequest request,
        CandidateSet candidates,
        Forecast forecast,
        List<PlanEvidence> evidence,
        Map<Integer, DraftDay> lockedDays,
        Map<String, Place> lockedPlaces,
        String instruction) {

    public PlanContext(TripRequest request, CandidateSet candidates, Forecast forecast, List<PlanEvidence> evidence,
            Map<Integer, DraftDay> lockedDays, Map<String, Place> lockedPlaces) {
        this(request, candidates, forecast, evidence, lockedDays, lockedPlaces, null);
    }

    public PlanContext {
        evidence = List.copyOf(evidence);
        lockedDays = Map.copyOf(lockedDays);
        lockedPlaces = Map.copyOf(lockedPlaces);
    }

    public static PlanContext of(TripRequest request, CandidateSet candidates, Forecast forecast,
            List<PlanEvidence> evidence) {
        return new PlanContext(request, candidates, forecast, evidence, Map.of(), Map.of());
    }

    /** Candidate places plus places of locked days: everything a valid draft may reference. */
    public Map<String, Place> knownPlaces() {
        Map<String, Place> map = new HashMap<>(lockedPlaces);
        candidates.all().forEach(c -> map.put(c.id(), c.place()));
        return map;
    }

    public Map<String, ScoredPlace> candidatesById() {
        return candidates.byId();
    }

    public Set<String> evidenceIds() {
        return evidence.stream().map(PlanEvidence::evId).collect(Collectors.toSet());
    }

    public LocalDate date(int dayIndex) {
        return request.dateOf(dayIndex);
    }

    public boolean isRainy(int dayIndex) {
        return forecast.isRainy(date(dayIndex));
    }

    /** Re-planning part of an existing trip (uses the replan prompt). */
    public boolean isReplan() {
        return !lockedDays.isEmpty() || (instruction != null && !instruction.isBlank());
    }

    public boolean isLocked(int dayIndex) {
        return lockedDays.containsKey(dayIndex);
    }
}
