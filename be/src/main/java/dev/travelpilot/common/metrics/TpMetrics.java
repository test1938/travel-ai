package dev.travelpilot.common.metrics;

/**
 * Names of the application metrics (Micrometer), readable at {@code /actuator/metrics/<name>}. Tags are always
 * from small fixed sets (step, operation, tool, kind), never ids or user text.
 */
public final class TpMetrics {

    /** Timer per planning step: weather, candidates, retrieve, llm, assemble, total. */
    public static final String PLAN_LATENCY = "tp.plan.latency";
    /** Distribution of repair calls per plan. */
    public static final String PLAN_REPAIRS = "tp.plan.repairs";
    /** Counter of plans that used the greedy fallback, tagged with the reason. */
    public static final String PLAN_FALLBACK = "tp.plan.fallback";
    /** Counter of model tokens, tagged with operation and type (input/output). */
    public static final String LLM_TOKENS = "tp.llm.tokens";
    /** Timer per model call, tagged with operation and status. */
    public static final String LLM_LATENCY = "tp.llm.latency";
    /** Counter of estimated model cost in USD, tagged with operation (models without a price are not counted). */
    public static final String LLM_COST = "tp.llm.cost";
    /** Counter of retried model calls, tagged with operation and reason (429, 5xx, timeout, transient). */
    public static final String LLM_RETRIES = "tp.llm.retries";
    /** Timer per chat tool execution, tagged with tool and status. */
    public static final String TOOL_LATENCY = "tp.tool.latency";
    /** Counter of requests refused by the rate limiter, tagged with kind. */
    public static final String RATELIMIT_REJECTED = "tp.ratelimit.rejected";

    private TpMetrics() {}
}
