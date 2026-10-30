package dev.travelpilot.common.ai;

import dev.travelpilot.common.ai.AiCallLog.Status;
import dev.travelpilot.common.metrics.TpMetrics;
import dev.travelpilot.common.web.RequestIdFilter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Records every model call and every chat tool execution: one ai_call_logs row (with the estimated cost from
 * {@link AiPricing}) plus the tp.llm.* / tp.tool.* metrics. Logging must never break a request: a failed insert is only reported in the application log.
 */
@Component
public class AiCallLogger {

    private static final Logger log = LoggerFactory.getLogger(AiCallLogger.class);

    public static final String TOOL_OPERATION = "TOOL";
    /** Metric tag for tool names the model made up, so they cannot grow the number of time series. */
    static final String UNKNOWN_TOOL = "unknown";
    private static final int MAX_TOOL_NAME = 64;

    private final AiCallLogStore store;
    private final MeterRegistry meters;
    private final AiPricing pricing;

    /** Without metrics or prices, for unit tests. */
    public AiCallLogger(AiCallLogStore store) {
        this(store, new SimpleMeterRegistry(), AiPricing.none());
    }

    @Autowired
    public AiCallLogger(AiCallLogStore store, MeterRegistry meters, AiPricing pricing) {
        this.store = store;
        this.meters = meters;
        this.pricing = pricing;
    }

    /** For unit tests that do not care about logs. */
    public static AiCallLogger none() {
        return new AiCallLogger(row -> {});
    }

    public void modelCall(AiCall call, LlmUsage usage, long latencyMs, int attempts, Status status,
            String errorCode) {
        meters.counter(TpMetrics.LLM_TOKENS, "operation", call.operation(), "type", "input")
                .increment(usage.tokensIn());
        meters.counter(TpMetrics.LLM_TOKENS, "operation", call.operation(), "type", "output")
                .increment(usage.tokensOut());
        Timer.builder(TpMetrics.LLM_LATENCY).tags("operation", call.operation(), "status", status.name())
                .register(meters).record(Duration.ofMillis(latencyMs));
        BigDecimal cost = pricing.cost(usage.model(), usage.tokensIn(), usage.tokensOut()).orElse(null);
        if (cost != null) {
            meters.counter(TpMetrics.LLM_COST, "operation", call.operation()).increment(cost.doubleValue());
        }
        write(new AiCallLog(requestId(), call.operation(), usage.model(), call.promptVersion(), usage.tokensIn(),
                usage.tokensOut(), latencyMs, attempts, status, errorCode, call.retrievedIds(), List.of(), cost));
    }

    /** Counts a retry of a model call; the call itself is logged once, with its number of attempts. */
    public void retry(AiCall call, String reason) {
        meters.counter(TpMetrics.LLM_RETRIES, "operation", call.operation(), "reason", reason).increment();
    }

    /** {@code knownTool} = the name is one of our @Tool methods (only those become metric tags). */
    public void toolCall(String toolName, boolean knownTool, boolean ok, long latencyMs) {
        Status status = ok ? Status.OK : Status.REJECTED;
        Timer.builder(TpMetrics.TOOL_LATENCY).tags("tool", knownTool ? toolName : UNKNOWN_TOOL, "status",
                status.name()).register(meters).record(Duration.ofMillis(latencyMs));
        String name = String.valueOf(toolName);
        name = name.length() > MAX_TOOL_NAME ? name.substring(0, MAX_TOOL_NAME) : name;
        write(new AiCallLog(requestId(), TOOL_OPERATION, null, null, 0, 0, latencyMs, 1, status, null, List.of(),
                List.of(name), null));
    }

    private static String requestId() {
        return MDC.get(RequestIdFilter.MDC_KEY);
    }

    private void write(AiCallLog row) {
        try {
            store.insert(row);
        } catch (RuntimeException e) {
            log.warn("Could not write ai_call_logs row for {}: {}", row.operation(), e.getMessage());
        }
    }
}
