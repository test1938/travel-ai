package dev.travelpilot.common.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** All application-specific settings, bound from the {@code travelpilot.*} block of application.yml. */
@Validated
@ConfigurationProperties("travelpilot")
public record TravelPilotProperties(
        @NotNull @Valid Ai ai,
        @NotNull @Valid Scoring scoring,
        @NotNull @Valid Planner planner,
        @NotNull @Valid Rag rag) {

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
