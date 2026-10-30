package dev.travelpilot.recommendation;

import dev.travelpilot.catalog.Place;
import dev.travelpilot.common.config.TravelPilotProperties;
import dev.travelpilot.recommendation.ScoreComponents.BudgetFit;
import dev.travelpilot.recommendation.ScoreComponents.DistanceFit;
import dev.travelpilot.recommendation.ScoreComponents.GroupFit;
import dev.travelpilot.recommendation.ScoreComponents.Popularity;
import dev.travelpilot.recommendation.ScoreComponents.PreferenceMatch;
import dev.travelpilot.recommendation.ScoreComponents.ScheduleFit;

/**
 * Weighted sum of the six components (weights from travelpilot.scoring.weights) plus a small bonus for indoor
 * places when rain is forecast on any trip day.
 */
public final class Scorer {

    static final double RAIN_INDOOR_BONUS = 0.05;

    private final TravelPilotProperties.Weights weights;
    private final ScoreComponent preference = new PreferenceMatch();
    private final ScoreComponent budget = new BudgetFit();
    private final ScoreComponent distance = new DistanceFit();
    private final ScoreComponent schedule = new ScheduleFit();
    private final ScoreComponent group = new GroupFit();
    private final ScoreComponent popularity = new Popularity();

    public Scorer(TravelPilotProperties.Weights weights) {
        this.weights = weights;
    }

    public ScoreBreakdown score(Place place, ScoreComponent.Context ctx) {
        double p = preference.score(place, ctx);
        double b = budget.score(place, ctx);
        double d = distance.score(place, ctx);
        double s = schedule.score(place, ctx);
        double g = group.score(place, ctx);
        double pop = popularity.score(place, ctx);
        double rain = ctx.forecast().anyRain() && place.indoor() ? RAIN_INDOOR_BONUS : 0;
        double total = weights.preference() * p + weights.budget() * b + weights.distance() * d
                + weights.schedule() * s + weights.group() * g + weights.popularity() * pop + rain;
        return new ScoreBreakdown(round(total), round(p), round(b), round(d), round(s), round(g), round(pop), rain);
    }

    private static double round(double v) {
        return Math.round(v * 1000) / 1000.0;
    }
}
