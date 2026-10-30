package dev.travelpilot.planner;

import dev.travelpilot.catalog.Place;
import dev.travelpilot.common.domain.PlaceType;
import dev.travelpilot.integration.weather.Forecast;
import dev.travelpilot.integration.weather.WeatherProvider;
import dev.travelpilot.planner.TripPlanDraft.DraftDay;
import dev.travelpilot.recommendation.CandidateSet;
import dev.travelpilot.recommendation.HardFilter;
import dev.travelpilot.recommendation.RecommendationService;
import dev.travelpilot.trip.TripPlan;
import dev.travelpilot.trip.TripPlan.PlanMeta;
import dev.travelpilot.trip.TripRequest;
import dev.travelpilot.trip.Warning;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * The fixed planning workflow (ADR-005): weather -> candidates (filter + score) -> evidence -> LLM draft with
 * validation, repair and fallback -> assemble. Each step is timed. Weather problems never block planning.
 */
@Service
public class PlannerOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(PlannerOrchestrator.class);

    /** The plan plus how it was produced. */
    public record Result(TripPlan plan, RepairLoop.Outcome outcome, Map<String, Long> stepMillis) {}

    private final WeatherProvider weather;
    private final RecommendationService recommendations;
    private final EvidenceService evidence;
    private final RepairLoop repairLoop;
    private final Assembler assembler;

    public PlannerOrchestrator(WeatherProvider weather, RecommendationService recommendations,
            EvidenceService evidence, RepairLoop repairLoop, Assembler assembler) {
        this.weather = weather;
        this.recommendations = recommendations;
        this.evidence = evidence;
        this.repairLoop = repairLoop;
        this.assembler = assembler;
    }

    public Result plan(TripRequest req) {
        return plan(req, Map.of(), Map.of(), HardFilter.Options.defaults());
    }

    /**
     * Plans the trip; {@code lockedDays} (with their places) are kept unchanged and their activities are not
     * offered again (used when re-planning part of a trip).
     */
    public Result plan(TripRequest req, Map<Integer, DraftDay> lockedDays, Map<String, Place> lockedPlaces,
            HardFilter.Options options) {
        long start = System.currentTimeMillis();
        Map<String, Long> steps = new LinkedHashMap<>();
        List<Warning> warnings = new ArrayList<>();

        Forecast forecast = timed(steps, "weather", () -> safeForecast(req));
        if (forecast.isEmpty()) {
            warnings.add(Warning.of("WEATHER_UNAVAILABLE",
                    "Chưa có dự báo thời tiết cho ngày đi, lịch chưa tính tới khả năng mưa."));
        }

        Set<String> exclude = new HashSet<>(options.excludeIds());
        exclude.addAll(lockedActivityIds(lockedDays, lockedPlaces));
        CandidateSet candidates = timed(steps, "candidates",
                () -> recommendations.candidates(req, forecast, options.withExcludeIds(exclude)));
        List<PlanEvidence> passages = timed(steps, "retrieve", () -> evidence.forCandidates(req, candidates));

        PlanContext ctx = new PlanContext(req, candidates, forecast, passages, lockedDays, lockedPlaces);
        RepairLoop.Outcome outcome = timed(steps, "llm", () -> repairLoop.run(ctx));
        if (outcome.fallback()) {
            warnings.add(Warning.of("FALLBACK_USED",
                    "AI chưa tạo được lịch hợp lệ nên lịch được xếp tự động theo điểm phù hợp."));
        }

        long elapsed = System.currentTimeMillis() - start;
        PlanMeta meta = new PlanMeta(outcome.promptVersion(), outcome.model(), outcome.repaired(), outcome.repairs(),
                outcome.fallback(), elapsed);
        TripPlan plan = timed(steps, "assemble",
                () -> assembler.assemble(outcome.draft(), ctx, outcome.report().soft(), warnings, meta));
        log.info("Planned {} days in {} for {} ms (repairs={}, fallback={}, steps={})", req.days(),
                req.destination(), elapsed, outcome.repairs(), outcome.fallback(), steps);
        return new Result(plan, outcome, steps);
    }

    private Forecast safeForecast(TripRequest req) {
        try {
            Forecast f = weather.forecast(req.destination(), req.dates());
            return f == null ? Forecast.NONE : f;
        } catch (RuntimeException e) {
            log.warn("Weather provider failed: {}", e.getMessage());
            return Forecast.NONE;
        }
    }

    private static Set<String> lockedActivityIds(Map<Integer, DraftDay> lockedDays, Map<String, Place> places) {
        Set<String> ids = new HashSet<>();
        lockedDays.values().forEach(day -> day.items().forEach(i -> {
            Place p = places.get(i.placeId());
            if (p != null && p.type() == PlaceType.ACTIVITY) {
                ids.add(p.id());
            }
        }));
        return ids;
    }

    private static <T> T timed(Map<String, Long> steps, String name, Supplier<T> step) {
        long t0 = System.currentTimeMillis();
        try {
            return step.get();
        } finally {
            steps.put(name, System.currentTimeMillis() - t0);
        }
    }
}
