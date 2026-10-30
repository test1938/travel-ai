package dev.travelpilot.planner;

import dev.travelpilot.catalog.Place;
import dev.travelpilot.common.domain.PlaceType;
import dev.travelpilot.common.metrics.TpMetrics;
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
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
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
    private final MeterRegistry meters;

    /** Without metrics, for unit tests. */
    public PlannerOrchestrator(WeatherProvider weather, RecommendationService recommendations,
            EvidenceService evidence, RepairLoop repairLoop, Assembler assembler) {
        this(weather, recommendations, evidence, repairLoop, assembler, new SimpleMeterRegistry());
    }

    @Autowired
    public PlannerOrchestrator(WeatherProvider weather, RecommendationService recommendations,
            EvidenceService evidence, RepairLoop repairLoop, Assembler assembler, MeterRegistry meters) {
        this.weather = weather;
        this.recommendations = recommendations;
        this.evidence = evidence;
        this.repairLoop = repairLoop;
        this.assembler = assembler;
        this.meters = meters;
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
        return plan(req, lockedDays, lockedPlaces, options, null);
    }

    /** Re-planning variant: {@code instruction} is the traveler's change, passed to the replan prompt. */
    public Result plan(TripRequest req, Map<Integer, DraftDay> lockedDays, Map<String, Place> lockedPlaces,
            HardFilter.Options options, String instruction) {
        long start = System.currentTimeMillis();
        Map<String, Long> steps = new LinkedHashMap<>();
        List<Warning> warnings = new ArrayList<>();

        Forecast forecast = timed(steps, "weather", () -> safeForecast(req));
        warnings.addAll(weatherWarnings(req, forecast));

        Set<String> exclude = new HashSet<>(options.excludeIds());
        exclude.addAll(lockedActivityIds(lockedDays, lockedPlaces));
        CandidateSet candidates = timed(steps, "candidates",
                () -> recommendations.candidates(req, forecast, options.withExcludeIds(exclude)));
        List<PlanEvidence> passages = timed(steps, "retrieve", () -> evidence.forCandidates(req, candidates));

        PlanContext ctx = new PlanContext(req, candidates, forecast, passages, lockedDays, lockedPlaces, instruction);
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
        record(outcome, System.currentTimeMillis() - start);
        return new Result(plan, outcome, steps);
    }

    /**
     * One trip-level warning when there is no forecast at all; otherwise one per day without data (forecasts only
     * cover the next 16 days, so a long or distant trip is partly covered).
     */
    static List<Warning> weatherWarnings(TripRequest req, Forecast forecast) {
        if (forecast.isEmpty()) {
            return List.of(Warning.of("WEATHER_UNAVAILABLE", "Chưa có dự báo thời tiết cho ngày đi (dự báo chỉ có "
                    + "cho 16 ngày tới), lịch chưa tính tới khả năng mưa."));
        }
        List<Warning> out = new ArrayList<>();
        for (int day = 1; day <= req.days(); day++) {
            if (!forecast.days().containsKey(req.dateOf(day))) {
                out.add(new Warning("WEATHER_UNAVAILABLE",
                        "Ngày " + day + " chưa có dự báo thời tiết, lịch chưa tính tới khả năng mưa.", day, null));
            }
        }
        return out;
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

    private <T> T timed(Map<String, Long> steps, String name, Supplier<T> step) {
        long t0 = System.currentTimeMillis();
        try {
            return step.get();
        } finally {
            long ms = System.currentTimeMillis() - t0;
            steps.put(name, ms);
            stepTimer(name).record(Duration.ofMillis(ms));
        }
    }

    private void record(RepairLoop.Outcome outcome, long totalMs) {
        stepTimer("total").record(Duration.ofMillis(totalMs));
        DistributionSummary.builder(TpMetrics.PLAN_REPAIRS).register(meters).record(outcome.repairs());
        if (outcome.fallback()) {
            meters.counter(TpMetrics.PLAN_FALLBACK, "reason", String.valueOf(outcome.fallbackReason())).increment();
        }
    }

    private Timer stepTimer(String step) {
        return Timer.builder(TpMetrics.PLAN_LATENCY).tag("step", step).register(meters);
    }
}
