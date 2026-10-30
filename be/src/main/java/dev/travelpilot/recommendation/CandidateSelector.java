package dev.travelpilot.recommendation;

import dev.travelpilot.catalog.Place;
import dev.travelpilot.common.config.TravelPilotProperties;
import dev.travelpilot.common.domain.PlaceType;
import dev.travelpilot.integration.weather.Forecast;
import dev.travelpilot.trip.TripRequest;
import dev.travelpilot.trip.Warning;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Hard filter, score and keep the top candidates. When too few places survive, relaxes the price cap once
 * (50% -> 70% of the budget) and reports it; never returns an empty set while age-appropriate places exist.
 */
public final class CandidateSelector {

    public static final double RELAXED_MAX_COST_SHARE = 0.7;
    static final int ACTIVITIES_PER_DAY = 4;
    static final int FOOD_PER_DAY = 3;
    static final int MIN_ACTIVITIES = 8;
    static final int MIN_FOOD = 6;
    static final int MEALS_PER_DAY = 2;

    private final Scorer scorer;

    public CandidateSelector(TravelPilotProperties.Weights weights) {
        this.scorer = new Scorer(weights);
    }

    public CandidateSet select(List<Place> places, TripRequest req, Forecast forecast, HardFilter.Options options) {
        int neededActivities = req.days() * req.pace().maxActivitiesPerDay();
        int neededMeals = req.days() * MEALS_PER_DAY;
        List<Warning> warnings = new ArrayList<>();

        HardFilter.Result filtered = HardFilter.apply(places, req, options);
        if ((count(filtered, PlaceType.ACTIVITY) < neededActivities || count(filtered, PlaceType.FOOD) < neededMeals)
                && options.maxCostShare() < RELAXED_MAX_COST_SHARE) {
            HardFilter.Result relaxed = HardFilter.apply(places, req, options.withMaxCostShare(RELAXED_MAX_COST_SHARE));
            if (relaxed.kept().size() > filtered.kept().size()) {
                filtered = relaxed;
                warnings.add(Warning.of("CANDIDATES_RELAXED",
                        "Ít lựa chọn phù hợp nên đã cho phép mục đắt hơn (tối đa 70% ngân sách cho cả nhóm)."));
            }
        }

        ScoreComponent.Context ctx = new ScoreComponent.Context(req, forecast);
        List<ScoredPlace> activities = top(filtered, PlaceType.ACTIVITY, ctx,
                Math.max(MIN_ACTIVITIES, req.days() * ACTIVITIES_PER_DAY));
        List<ScoredPlace> food = top(filtered, PlaceType.FOOD, ctx, Math.max(MIN_FOOD, req.days() * FOOD_PER_DAY));

        if (activities.size() < neededActivities) {
            warnings.add(Warning.of("CANDIDATES_SHORT", "Chỉ có %d hoạt động phù hợp cho %d ngày, lịch có thể ít hoạt động hơn."
                    .formatted(activities.size(), req.days())));
        }
        if (food.size() < neededMeals) {
            warnings.add(Warning.of("FOOD_SHORT", "Ít quán ăn phù hợp, một số bữa có thể lặp lại quán đã chọn."));
        }
        return new CandidateSet(activities, food, warnings, filtered.rejected());
    }

    private List<ScoredPlace> top(HardFilter.Result filtered, PlaceType type, ScoreComponent.Context ctx, int limit) {
        return filtered.kept().stream()
                .filter(p -> p.type() == type)
                .map(p -> new ScoredPlace(p, scorer.score(p, ctx)))
                .sorted(Comparator.comparingDouble((ScoredPlace s) -> s.score().total()).reversed()
                        .thenComparing(ScoredPlace::id))
                .limit(limit)
                .toList();
    }

    private static long count(HardFilter.Result r, PlaceType type) {
        return r.kept().stream().filter(p -> p.type() == type).count();
    }
}
