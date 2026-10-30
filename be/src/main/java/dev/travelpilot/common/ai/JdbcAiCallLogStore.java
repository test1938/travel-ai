package dev.travelpilot.common.ai;

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
}
