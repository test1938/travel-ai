package dev.travelpilot.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import dev.travelpilot.common.config.TravelPilotProperties;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.TimeMeter;
import java.time.Duration;
import java.util.OptionalLong;
import java.util.concurrent.TimeUnit;

/**
 * In-memory token buckets (Bucket4j): per guest for planning and chat, per IP for new guest tokens, plus one
 * daily cap shared by every model-backed request. Single instance only; a cluster would need a shared store.
 */
public class RateLimiter {

    /** What is being limited. PLAN and MESSAGE also count against the daily model cap. */
    public enum Kind { PLAN, MESSAGE, GUEST }

    private final TravelPilotProperties.RateLimit cfg;
    private final TimeMeter time;
    private final Cache<String, Bucket> buckets = Caffeine.newBuilder()
            .expireAfterAccess(Duration.ofHours(2))
            .maximumSize(100_000)
            .build();
    private final Bucket daily;

    public RateLimiter(TravelPilotProperties.RateLimit cfg) {
        this(cfg, TimeMeter.SYSTEM_MILLISECONDS);
    }

    /** {@code time} lets tests move the clock. */
    RateLimiter(TravelPilotProperties.RateLimit cfg, TimeMeter time) {
        this.cfg = cfg;
        this.time = time;
        this.daily = bucket(cfg.modelRequestsPerDay(), Duration.ofDays(1));
    }

    public boolean enabled() {
        return cfg.enabled();
    }

    /**
     * Consumes one request for {@code key} (guest id or IP). Empty = allowed; otherwise the seconds to wait,
     * for the Retry-After header.
     */
    public OptionalLong tryConsume(Kind kind, String key) {
        Bucket own = buckets.get(kind + ":" + key, k -> switch (kind) {
            case PLAN -> bucket(cfg.plansPerHour(), Duration.ofHours(1));
            case MESSAGE -> bucket(cfg.messagesPerHour(), Duration.ofHours(1));
            case GUEST -> bucket(cfg.guestsPerHourPerIp(), Duration.ofHours(1));
        });
        OptionalLong wait = consume(own);
        if (wait.isPresent() || kind == Kind.GUEST) {
            return wait;
        }
        return consume(daily);
    }

    private static OptionalLong consume(Bucket bucket) {
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
        if (probe.isConsumed()) {
            return OptionalLong.empty();
        }
        return OptionalLong.of(Math.max(1, TimeUnit.NANOSECONDS.toSeconds(probe.getNanosToWaitForRefill()) + 1));
    }

    /** Full at start, refilled gradually over the period (a burst of {@code capacity} is allowed). */
    private Bucket bucket(int capacity, Duration period) {
        return Bucket.builder()
                .addLimit(limit -> limit.capacity(capacity).refillGreedy(capacity, period))
                .withCustomTimePrecision(time)
                .build();
    }
}
