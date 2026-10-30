package dev.travelpilot.common.ai;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcAiCallLogStore implements AiCallLogStore {

    private final JdbcClient jdbc;

    public JdbcAiCallLogStore(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void insert(AiCallLog row) {
        jdbc.sql("""
                INSERT INTO ai_call_logs (request_id, operation, model, prompt_version, tokens_in, tokens_out,
                    latency_ms, attempts, status, error_code, retrieved_ids, tool_calls, cost_usd)
                VALUES (:requestId, :operation, :model, :promptVersion, :tokensIn, :tokensOut,
                    :latencyMs, :attempts, :status, :errorCode, :retrievedIds, :toolCalls, :costUsd)
                """)
                .param("requestId", row.requestId())
                .param("operation", row.operation())
                .param("model", row.model())
                .param("promptVersion", row.promptVersion())
                .param("tokensIn", row.tokensIn())
                .param("tokensOut", row.tokensOut())
                .param("latencyMs", (int) Math.min(row.latencyMs(), Integer.MAX_VALUE))
                .param("attempts", row.attempts())
                .param("status", row.status().name())
                .param("errorCode", row.errorCode())
                .param("retrievedIds", row.retrievedIds().toArray(String[]::new))
                .param("toolCalls", row.toolCalls().toArray(String[]::new))
                .param("costUsd", row.costUsd())
                .update();
    }

    /** Usage per operation and the average cost of producing one itinerary (T7.05). */
    public AiCostReport report(int days) {
        List<AiCostReport.OperationCost> operations = jdbc.sql("""
                SELECT operation, count(*) AS calls, avg(tokens_in) AS avg_in, avg(tokens_out) AS avg_out,
                       avg(latency_ms) AS avg_ms, coalesce(sum(cost_usd), 0) AS cost
                FROM ai_call_logs
                WHERE created_at > now() - make_interval(days => :days)
                GROUP BY operation
                ORDER BY operation
                """)
                .param("days", days)
                .query((rs, n) -> new AiCostReport.OperationCost(rs.getString("operation"), rs.getLong("calls"),
                        rs.getDouble("avg_in"), rs.getDouble("avg_out"), rs.getDouble("avg_ms"),
                        rs.getBigDecimal("cost")))
                .list();
        AiCostReport.PlanCost plans = jdbc.sql("""
                SELECT count(*) AS plans, coalesce(avg(calls), 0) AS avg_calls, coalesce(avg(tokens_in), 0) AS avg_in,
                       coalesce(avg(tokens_out), 0) AS avg_out, avg(cost) AS avg_cost
                FROM (SELECT request_id, count(*) AS calls, sum(tokens_in) AS tokens_in,
                             sum(tokens_out) AS tokens_out, sum(cost_usd) AS cost
                      FROM ai_call_logs
                      WHERE request_id IS NOT NULL AND operation IN ('PLAN', 'REPAIR')
                        AND created_at > now() - make_interval(days => :days)
                      GROUP BY request_id
                      HAVING bool_or(operation = 'PLAN')) per_plan
                """)
                .param("days", days)
                .query((rs, n) -> new AiCostReport.PlanCost(rs.getLong("plans"), rs.getDouble("avg_calls"),
                        rs.getDouble("avg_in"), rs.getDouble("avg_out"), scaled(rs.getBigDecimal("avg_cost"))))
                .single();
        return new AiCostReport(days, operations, plans);
    }

    private static BigDecimal scaled(BigDecimal value) {
        return value == null ? null : value.setScale(6, RoundingMode.HALF_UP);
    }
}
