package dev.travelpilot.common.ai;

import static org.assertj.core.api.Assertions.assertThat;

import dev.travelpilot.support.IntegrationTest;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

/** Needs Docker: V5 table, text[] columns and the status check constraint. */
@IntegrationTest
class JdbcAiCallLogStoreTest {

    @Autowired
    JdbcAiCallLogStore store;

    @Autowired
    JdbcClient jdbc;

    @Test
    void insertsOneRowWithArrays() {
        store.insert(new AiCallLog("req-jdbc-1", "ASK", "gemini-2.5-flash", "ask-v3", 1200, 300, 850, 2,
                AiCallLog.Status.OK, null, List.of("danang-tips-1", "danang-place-2"), List.of(),
                new BigDecimal("0.00111000")));

        Map<String, Object> row = jdbc.sql("""
                SELECT operation, tokens_in, attempts, status, array_to_string(retrieved_ids, ',') AS ids, cost_usd
                FROM ai_call_logs WHERE request_id = 'req-jdbc-1'""").query().singleRow();
        assertThat(row).containsEntry("operation", "ASK").containsEntry("tokens_in", 1200)
                .containsEntry("status", "OK").containsEntry("ids", "danang-tips-1,danang-place-2");
        assertThat(((Number) row.get("attempts")).intValue()).isEqualTo(2);
        assertThat((BigDecimal) row.get("cost_usd")).isEqualByComparingTo("0.00111");
    }

    @Test
    void reportAveragesTheCostOfOneItinerary() {
        jdbc.sql("DELETE FROM ai_call_logs").update();
        store.insert(row("req-a", "PLAN", "0.005"));
        store.insert(row("req-a", "REPAIR", "0.006"));
        store.insert(row("req-b", "PLAN", "0.004"));
        store.insert(row("req-c", "ASK", "0.001"));            // not a plan
        store.insert(row("req-c", "INTENT", "0.0001"));

        AiCostReport report = store.report(7);

        assertThat(report.plans().plans()).isEqualTo(2);
        assertThat(report.plans().avgModelCalls()).isEqualTo(1.5);
        assertThat(report.plans().avgCostUsd()).isEqualByComparingTo("0.0075");   // (0.011 + 0.004) / 2
        assertThat(report.operations()).extracting(AiCostReport.OperationCost::operation)
                .containsExactly("ASK", "INTENT", "PLAN", "REPAIR");
    }

    private static AiCallLog row(String requestId, String operation, String cost) {
        return new AiCallLog(requestId, operation, "gemini-2.5-flash", "v1", 1000, 500, 100, 1, AiCallLog.Status.OK,
                null, List.of(), List.of(), new BigDecimal(cost));
    }
}
