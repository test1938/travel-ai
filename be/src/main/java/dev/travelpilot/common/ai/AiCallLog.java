package dev.travelpilot.common.ai;

import java.math.BigDecimal;
import java.util.List;

/** One row of ai_call_logs. Never contains prompt or answer text. */
public record AiCallLog(
        String requestId,
        String operation,
        String model,
        String promptVersion,
        int tokensIn,
        int tokensOut,
        long latencyMs,
        int attempts,
        Status status,
        String errorCode,
        List<String> retrievedIds,
        List<String> toolCalls,
        BigDecimal costUsd) {

    /** OK, TIMEOUT and ERROR for model calls; OK or REJECTED for tool executions. */
    public enum Status { OK, TIMEOUT, ERROR, REJECTED }

    public AiCallLog {
        retrievedIds = List.copyOf(retrievedIds);
        toolCalls = List.copyOf(toolCalls);
    }
}
