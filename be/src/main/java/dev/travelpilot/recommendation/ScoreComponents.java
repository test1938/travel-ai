package dev.travelpilot.recommendation;

import dev.travelpilot.catalog.Place;
import dev.travelpilot.common.Geo;
import dev.travelpilot.common.domain.BestTime;
import dev.travelpilot.common.domain.Destination;
import dev.travelpilot.common.domain.PlaceTag;
import dev.travelpilot.common.domain.PlaceType;
import dev.travelpilot.trip.TripRequest;
import java.util.Set;

/** The six score components of plan/03-ai-pipeline.md §2, each a small pure strategy. */
public final class ScoreComponents {

    private ScoreComponents() {}

    /** |tags ∩ preferences| / |preferences|; 0.5 when the traveler gave no preference. */
    public static final class PreferenceMatch implements ScoreComponent {
        @Override
        public double score(Place place, Context ctx) {
            Set<PlaceTag> prefs = ctx.request().preferences();
            if (prefs.isEmpty()) {
                return 0.5;
            }
            long matches = place.tags().stream().filter(prefs::contains).count();
            return ScoreComponent.clamp((double) matches / prefs.size());
        }
    }

    /**
     * 1 when the group cost is at most half of the per-item budget, falling linearly to 0 at 1.5x. Activities get
     * 60% of the budget spread over the planned activities, meals 40% over two meals a day.
     */
    public static final class BudgetFit implements ScoreComponent {
        static final double ACTIVITY_SHARE = 0.6;
        static final double FOOD_SHARE = 0.4;
        static final int MEALS_PER_DAY = 2;

        @Override
        public double score(Place place, Context ctx) {
            TripRequest req = ctx.request();
            double perItem = place.type() == PlaceType.FOOD
                    ? req.budgetVnd() * FOOD_SHARE / (req.days() * MEALS_PER_DAY)
                    : req.budgetVnd() * ACTIVITY_SHARE / (req.days() * req.pace().maxActivitiesPerDay());
            double cost = req.groupCost(place);
            if (cost <= 0.5 * perItem) {
                return 1;
            }
            return ScoreComponent.clamp((1.5 * perItem - cost) / perItem);
        }
    }

    /** Closer to the destination center is better; day trips keep a floor so a matching one can still win. */
    public static final class DistanceFit implements ScoreComponent {
        static final double RANGE_KM = 15;
        static final double DAY_TRIP_FLOOR = 0.3;

        @Override
        public double score(Place place, Context ctx) {
            Destination d = ctx.request().destination();
            double km = Geo.km(place.lat(), place.lon(), d.centerLat(), d.centerLon());
            double s = 1 - Math.min(1, km / RANGE_KM);
            return place.dayTrip() ? Math.max(s, DAY_TRIP_FLOOR) : s;
        }
    }

    /** Long visits suit a packed pace, short ones a relaxed pace; flexible best time gets a small bonus. */
    public static final class ScheduleFit implements ScoreComponent {
        @Override
        public double score(Place place, Context ctx) {
            int min = place.durationMin();
            double s = switch (ctx.request().pace()) {
                case RELAXED -> min <= 180 ? 1 : min <= 300 ? 0.6 : 0.3;
                case NORMAL -> min <= 300 ? 1 : 0.6;
                case PACKED -> 1;
            };
            return ScoreComponent.clamp(s + (place.bestTime() == BestTime.ANY ? 0.1 : 0));
        }
    }

    /** Fit with who is travelling: families, couples or groups of friends. */
    public static final class GroupFit implements ScoreComponent {
        @Override
        public double score(Place place, Context ctx) {
            TripRequest req = ctx.request();
            if (req.hasChildren()) {
                if (place.hasTag(PlaceTag.NIGHTLIFE) || place.childMinAge() >= 12) {
                    return 0;
                }
                return place.hasTag(PlaceTag.FAMILY) || place.hasTag(PlaceTag.KIDS) ? 1 : 0.5;
            }
            if (req.adults() == 2) {
                return anyTag(place, PlaceTag.ROMANTIC, PlaceTag.PHOTO, PlaceTag.CAFE) ? 1 : 0.5;
            }
            if (req.adults() >= 3) {
                return anyTag(place, PlaceTag.ADVENTURE, PlaceTag.NIGHTLIFE, PlaceTag.BOAT) ? 1 : 0.5;
            }
            return 0.5;
        }

        private static boolean anyTag(Place place, PlaceTag... tags) {
            for (PlaceTag t : tags) {
                if (place.hasTag(t)) {
                    return true;
                }
            }
            return false;
        }
    }

    /** Dataset popularity proxy, already in [0, 1]. */
    public static final class Popularity implements ScoreComponent {
        @Override
        public double score(Place place, Context ctx) {
            return ScoreComponent.clamp(place.popularity());
        }
    }
}
