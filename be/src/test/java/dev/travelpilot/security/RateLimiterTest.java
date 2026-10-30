package dev.travelpilot.security;

import static org.assertj.core.api.Assertions.assertThat;

import dev.travelpilot.common.config.TravelPilotProperties;
import dev.travelpilot.security.RateLimiter.Kind;
import io.github.bucket4j.TimeMeter;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class RateLimiterTest {

    /** Manual clock for Bucket4j (milliseconds). */
    static final class ManualTime implements TimeMeter {
        final AtomicLong millis = new AtomicLong();

        @Override
        public long currentTimeNanos() {
            return Duration.ofMillis(millis.get()).toNanos();
        }

        @Override
        public boolean isWallClockBased() {
            return false;
        }

        void advance(Duration d) {
            millis.addAndGet(d.toMillis());
        }
    }

    private final ManualTime time = new ManualTime();

    private RateLimiter limiter(int plans, int messages, int guests, int daily) {
        return new RateLimiter(new TravelPilotProperties.RateLimit(true, plans, messages, guests, daily), time);
    }

    @Test
    void eleventhPlanInAnHourIsRejectedWithRetryAfter() {
        RateLimiter limiter = limiter(10, 60, 20, 2000);
        for (int i = 0; i < 10; i++) {
            assertThat(limiter.tryConsume(Kind.PLAN, "guest:a")).isEmpty();
        }
        assertThat(limiter.tryConsume(Kind.PLAN, "guest:a").orElseThrow()).isBetween(1L, 361L);

        time.advance(Duration.ofMinutes(6));   // one token back (10 per hour, refilled gradually)
        assertThat(limiter.tryConsume(Kind.PLAN, "guest:a")).isEmpty();
    }

    @Test
    void guestsAndKindsHaveSeparateBuckets() {
        RateLimiter limiter = limiter(1, 1, 1, 2000);
        assertThat(limiter.tryConsume(Kind.PLAN, "guest:a")).isEmpty();
        assertThat(limiter.tryConsume(Kind.PLAN, "guest:a")).isPresent();
        assertThat(limiter.tryConsume(Kind.PLAN, "guest:b")).isEmpty();
        assertThat(limiter.tryConsume(Kind.MESSAGE, "guest:a")).isEmpty();
    }

    @Test
    void dailyCapCoversAllModelRequestsButNotGuestCreation() {
        RateLimiter limiter = limiter(10, 60, 20, 3);
        assertThat(limiter.tryConsume(Kind.PLAN, "guest:a")).isEmpty();
        assertThat(limiter.tryConsume(Kind.MESSAGE, "guest:b")).isEmpty();
        assertThat(limiter.tryConsume(Kind.MESSAGE, "guest:c")).isEmpty();
        assertThat(limiter.tryConsume(Kind.MESSAGE, "guest:d")).isPresent();   // fourth model request today
        assertThat(limiter.tryConsume(Kind.GUEST, "ip:1.2.3.4")).isEmpty();
    }

    @Test
    void onlyExpensivePostsAreLimited() {
        assertThat(RateLimitInterceptor.kindOf("POST", "/api/v1/trips")).contains(Kind.PLAN);
        assertThat(RateLimitInterceptor.kindOf("POST", "/api/v1/trips/stream")).contains(Kind.PLAN);
        assertThat(RateLimitInterceptor.kindOf("POST", "/api/v1/trips/abc/messages")).contains(Kind.MESSAGE);
        assertThat(RateLimitInterceptor.kindOf("POST", "/api/v1/ask")).contains(Kind.MESSAGE);
        assertThat(RateLimitInterceptor.kindOf("POST", "/api/v1/guest")).contains(Kind.GUEST);
        assertThat(RateLimitInterceptor.kindOf("GET", "/api/v1/trips/abc/messages")).isEmpty();
        assertThat(RateLimitInterceptor.kindOf("GET", "/api/v1/places")).isEmpty();
    }
}
