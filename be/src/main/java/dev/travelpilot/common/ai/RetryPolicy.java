package dev.travelpilot.common.ai;

import com.openai.errors.OpenAIIoException;
import com.openai.errors.OpenAIServiceException;
import dev.travelpilot.common.config.TravelPilotProperties;
import dev.travelpilot.common.error.ApiException;
import dev.travelpilot.common.error.ErrorCode;
import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.Optional;
import java.util.random.RandomGenerator;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.web.client.RestClientResponseException;

/**
 * Which failed model calls are worth another try, and how long to wait. Only rate limits (429), server errors
 * (5xx) and timeouts are retried: a 400 (bad request, blocked content, invalid key) fails the same way again.
 */
public record RetryPolicy(int maxRetries, Duration initialBackoff, Duration maxBackoff) {

    public static RetryPolicy of(TravelPilotProperties.Retry cfg) {
        return new RetryPolicy(cfg.maxRetries(), Duration.ofMillis(cfg.initialBackoffMs()),
                Duration.ofMillis(cfg.maxBackoffMs()));
    }

    /**
     * Wait before retry {@code n} (1-based): exponential, capped, with "equal jitter" (half fixed, half random) so
     * that many requests failing together do not retry in lockstep.
     */
    public Duration backoff(int n, RandomGenerator random) {
        long exp = initialBackoff.toMillis() << Math.min(n - 1, 20);
        long cap = Math.min(exp, maxBackoff.toMillis());
        long half = cap / 2;
        return Duration.ofMillis(half + random.nextLong(cap - half + 1));
    }

    /** The reason to retry ("429", "5xx", "timeout"), or empty when the error is permanent. */
    public static Optional<String> retryReason(ApiException e) {
        if (e.code() == ErrorCode.LLM_TIMEOUT) {
            return Optional.of("timeout");
        }
        for (Throwable t = e.getCause(); t != null; t = t.getCause() == t ? null : t.getCause()) {
            Optional<String> reason = switch (t) {
                case com.google.genai.errors.ApiException g -> byStatus(g.code());
                case OpenAIServiceException o -> byStatus(o.statusCode());
                case OpenAIIoException io -> Optional.of("io");
                case RestClientResponseException r -> byStatus(r.getStatusCode().value());
                case SocketTimeoutException s -> Optional.of("timeout");
                case HttpTimeoutException h -> Optional.of("timeout");
                case TransientAiException a -> Optional.of("transient");
                default -> null;
            };
            if (reason != null) {
                return reason;
            }
        }
        return Optional.empty();
    }

    private static Optional<String> byStatus(int status) {
        if (status == 429) {
            return Optional.of("429");
        }
        return status >= 500 && status < 600 ? Optional.of("5xx") : Optional.empty();
    }
}
