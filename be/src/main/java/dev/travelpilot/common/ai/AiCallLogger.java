package dev.travelpilot.common.ai;

import dev.travelpilot.common.ai.AiCallLog.Status;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

/**
 * Records every model call and every chat tool execution in ai_call_logs. Logging must never break a request:
 * a failed insert is only reported in the application log.
 */
@Component
public class AiCallLogger {

    private static final Logger log = LoggerFactory.getLogger(AiCallLogger.class);

    /** MDC key set by the request id filter; rows of one HTTP request share it. */
    public static final String REQUEST_ID = "requestId";

    public static final String TOOL_OPERATION = "TOOL";

    private final AiCallLogStore store;

    public AiCallLogger(AiCallLogStore store) {
        this.store = store;
    }

    /** For unit tests that do not care about logs. */
    public static AiCallLogger none() {
        return new AiCallLogger(row -> {});
    }

    public void modelCall(AiCall call, LlmUsage usage, long latencyMs, int attempts, Status status,
            String errorCode) {
        write(new AiCallLog(MDC.get(REQUEST_ID), call.operation(), usage.model(), call.promptVersion(),
                usage.tokensIn(), usage.tokensOut(), latencyMs, attempts, status, errorCode, call.retrievedIds(),
                List.of(), null));
    }

    public void toolCall(String toolName, boolean ok, long latencyMs) {
        write(new AiCallLog(MDC.get(REQUEST_ID), TOOL_OPERATION, null, null, 0, 0, latencyMs, 1,
                ok ? Status.OK : Status.REJECTED, null, List.of(), List.of(String.valueOf(toolName)), null));
    }

    private void write(AiCallLog row) {
        try {
            store.insert(row);
        } catch (RuntimeException e) {
            log.warn("Could not write ai_call_logs row for {}: {}", row.operation(), e.getMessage());
        }
    }
}
