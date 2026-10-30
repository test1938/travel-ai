package dev.travelpilot.common.ai;

import java.math.BigDecimal;
import java.util.List;

/**
 * Cost and usage over the last {@code days}, from ai_call_logs. {@code plans} groups PLAN and REPAIR rows by
 * request id: one group = producing one itinerary (new trip or re-plan).
 */
public record AiCostReport(int days, List<OperationCost> operations, PlanCost plans) {

    /** {@code totalCostUsd} only covers rows of models that have a price. */
    public record OperationCost(String operation, long calls, double avgTokensIn, double avgTokensOut,
            double avgLatencyMs, BigDecimal totalCostUsd) {}

    public record PlanCost(long plans, double avgModelCalls, double avgTokensIn, double avgTokensOut,
            BigDecimal avgCostUsd) {}
}
