package dev.travelpilot.trip;

import dev.travelpilot.common.domain.BestTime;
import dev.travelpilot.common.domain.PlaceType;
import dev.travelpilot.common.domain.Slot;
import dev.travelpilot.integration.weather.DayWeather;
import dev.travelpilot.knowledge.Source;
import dev.travelpilot.recommendation.ScoreBreakdown;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The itinerary returned to the frontend, assembled by the backend from the catalog: every name, price, time
 * and total here comes from data and code, never from the model. {@code tripId} is null until saved.
 */
public record TripPlan(
        UUID tripId,
        int version,
        String title,
        String summary,
        List<DayPlan> days,
        Budget budget,
        List<Warning> warnings,
        List<Source> sources,
        PlanMeta meta) {

    public TripPlan {
        days = List.copyOf(days);
        warnings = List.copyOf(warnings);
        sources = List.copyOf(sources);
    }

    public TripPlan withIdentity(UUID id, int newVersion) {
        return new TripPlan(id, newVersion, title, summary, days, budget, warnings, sources, meta);
    }

    public record DayPlan(int dayIndex, LocalDate date, DayWeather weather, List<PlanItem> items) {

        public DayPlan {
            items = List.copyOf(items);
        }
    }

    public record PlanItem(
            String placeId,
            String name,
            PlaceType type,
            Slot slot,
            LocalTime start,
            LocalTime end,
            int durationMin,
            long costVnd,
            boolean costEstimated,
            String priceNote,
            LocalDate verifyAt,
            boolean indoor,
            BestTime bestTime,
            String reason,
            List<String> sourceIds,
            ScoreBreakdown score,
            double lat,
            double lon) {

        public PlanItem {
            sourceIds = List.copyOf(sourceIds);
        }
    }

    /** Totals computed by BudgetCalculator; covers tickets and planned meals only. */
    public record Budget(long totalVnd, long limitVnd, Map<PlaceType, Long> byType, boolean overBudget) {

        public Budget {
            byType = Map.copyOf(byType);
        }
    }

    /** How the plan was produced, for the UI badge, logs and eval. */
    public record PlanMeta(String promptVersion, String model, boolean repaired, int repairs, boolean fallback,
            long latencyMs) {}
}
