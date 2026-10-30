package dev.travelpilot.common.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** All application-specific settings, bound from the {@code travelpilot.*} block of application.yml. */
@Validated
@ConfigurationProperties("travelpilot")
public record TravelPilotProperties(
        @NotNull @Valid Ai ai,
        @NotNull @Valid Scoring scoring,
        @NotNull @Valid Planner planner,
        @NotNull @Valid Rag rag,
        @NotNull @Valid Ingest ingest,
        @NotNull @Valid Weather weather,
        @NotNull @Valid Security security,
        @NotEmpty Map<String, String> prompts) {

    /** Prompt version per prompt name, e.g. ask -> v1 loads prompts/ask-v1.system.st and ask-v1.user.st. */
    public String promptVersion(String name) {
        String version = prompts.get(name);
        if (version == null) {
            throw new IllegalStateException("No version configured for prompt '" + name + "' (travelpilot.prompts)");
        }
        return version;
    }

    /**
     * Open-Meteo forecast settings. {@code enabled=false} falls back to "no forecast" (tests, offline demos).
     *
     * @param timeoutSeconds connect + read timeout; weather must never slow planning down much.
     * @param cacheMinutes how long one destination's forecast is reused.
     */
    public record Weather(boolean enabled, @NotBlank String baseUrl, @Min(1) @Max(30) int timeoutSeconds,
            @Min(1) @Max(1440) int cacheMinutes) {}

    /**
     * @param guestTokenSecret HMAC key for guest tokens (>= 32 chars). Blank = random key per start (dev only:
     *     tokens stop working after a restart).
     * @param maxBodyBytes larger request bodies are rejected with INPUT_TOO_LONG.
     */
    public record Security(String guestTokenSecret, @Min(1) @Max(30) int guestTokenDays,
            @Min(1024) @Max(1_048_576) int maxBodyBytes, @NotNull @Valid RateLimit rateLimit) {}

    /**
     * Per-guest hourly limits, new guest tokens per IP per hour, and one daily cap on all model-backed requests
     * (protects the AI budget even if many guests are created).
     */
    public record RateLimit(boolean enabled, @Min(1) int plansPerHour, @Min(1) int messagesPerHour,
            @Min(1) int guestsPerHourPerIp, @Min(1) int modelRequestsPerDay) {}

    /** @param batchSize documents per embedding request when ingesting knowledge. */
    public record Ingest(@Min(1) @Max(100) int batchSize) {}

    /** @param chatTimeoutSeconds hard limit for one chat call; exceeding it returns LLM_TIMEOUT (504). */
    public record Ai(@Min(1) @Max(120) int chatTimeoutSeconds) {}

    public record Scoring(@NotNull @Valid Weights weights) {}

    /** Weights of the six score components (plan/03-ai-pipeline.md §2). They must add up to 1.0. */
    public record Weights(
            @DecimalMin("0") @DecimalMax("1") double preference,
            @DecimalMin("0") @DecimalMax("1") double budget,
            @DecimalMin("0") @DecimalMax("1") double distance,
            @DecimalMin("0") @DecimalMax("1") double schedule,
            @DecimalMin("0") @DecimalMax("1") double group,
            @DecimalMin("0") @DecimalMax("1") double popularity) {

        public Weights {
            double sum = preference + budget + distance + schedule + group + popularity;
            if (Math.abs(sum - 1.0) > 0.001) {
                throw new IllegalArgumentException(
                        "travelpilot.scoring.weights must add up to 1.0 but the sum is " + sum);
            }
        }
    }

    public record Planner(@Min(0) @Max(5) int maxRepairs, @Min(1) @Max(120) int timeoutSeconds) {}

    public record Rag(@Min(1) @Max(20) int topK, @DecimalMin("0") @DecimalMax("1") double threshold) {}
}
