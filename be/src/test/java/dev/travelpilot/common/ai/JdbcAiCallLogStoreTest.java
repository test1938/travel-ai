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
}
