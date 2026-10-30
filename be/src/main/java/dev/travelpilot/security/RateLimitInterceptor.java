package dev.travelpilot.security;

import dev.travelpilot.common.error.ApiException;
import dev.travelpilot.common.error.ErrorCode;
import dev.travelpilot.common.metrics.TpMetrics;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Limits the expensive endpoints (each one calls the model) and guest creation. Runs after
 * {@link GuestAuthInterceptor}, so the key is the verified guest id, or the client IP when there is none.
 * Over the limit: 429 RATE_LIMITED with a Retry-After header.
 */
public class RateLimitInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(RateLimitInterceptor.class);
    private static final Pattern MESSAGES = Pattern.compile("/api/v1/trips/[^/]+/messages");

    private final RateLimiter limiter;
    private final MeterRegistry meters;

    public RateLimitInterceptor(RateLimiter limiter, MeterRegistry meters) {
        this.limiter = limiter;
        this.meters = meters;
    }

    /** Which limit applies to a request; empty for reads and cheap endpoints. */
    static Optional<RateLimiter.Kind> kindOf(String method, String path) {
        if (!"POST".equals(method)) {
            return Optional.empty();
        }
        return switch (path) {
            case "/api/v1/trips", "/api/v1/trips/stream" -> Optional.of(RateLimiter.Kind.PLAN);
            case "/api/v1/ask", "/api/v1/ai/chat" -> Optional.of(RateLimiter.Kind.MESSAGE);
            case "/api/v1/guest" -> Optional.of(RateLimiter.Kind.GUEST);
            default -> MESSAGES.matcher(path).matches() ? Optional.of(RateLimiter.Kind.MESSAGE) : Optional.empty();
        };
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // An async re-dispatch (e.g. the end of the plan stream) is the same request: count it once.
        if (!limiter.enabled() || request.getDispatcherType() == DispatcherType.ASYNC) {
            return true;
        }
        Optional<RateLimiter.Kind> kind = kindOf(request.getMethod(), request.getRequestURI());
        if (kind.isEmpty()) {
            return true;
        }
        Optional<Guest> guest = GuestAuthInterceptor.current(request);
        String key = kind.get() != RateLimiter.Kind.GUEST && guest.isPresent()
                ? "guest:" + guest.get().id()
                : "ip:" + request.getRemoteAddr();
        OptionalLong retryAfter = limiter.tryConsume(kind.get(), key);
        if (retryAfter.isPresent()) {
            log.info("Rate limited {} for {} (retry after {} s)", kind.get(),
                    guest.map(g -> "guest " + GuestTokenService.hash(g.id())).orElse("an IP"),
                    retryAfter.getAsLong());
            meters.counter(TpMetrics.RATELIMIT_REJECTED, "kind", kind.get().name()).increment();
            response.setHeader(HttpHeaders.RETRY_AFTER, Long.toString(retryAfter.getAsLong()));
            throw new ApiException(ErrorCode.RATE_LIMITED);
        }
        return true;
    }
}
