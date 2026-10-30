package dev.travelpilot.common.ai;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Model price list ({@code travelpilot.pricing.models}) in USD per million tokens, used to estimate the cost of
 * each ai_call_logs row. Prices are list prices copied by hand; check the provider's pricing page when they change.
 */
@Validated
@ConfigurationProperties("travelpilot.pricing")
public record AiPricing(@Valid List<ModelPrice> models) {

    private static final BigDecimal MILLION = BigDecimal.valueOf(1_000_000);

    /** {@code model} matches the reported model name exactly or as a prefix (e.g. "gemini-2.5-flash-001"). */
    public record ModelPrice(@NotBlank String model, @DecimalMin("0") BigDecimal inputUsdPerMillion,
            @DecimalMin("0") BigDecimal outputUsdPerMillion) {}

    public AiPricing {
        models = models == null ? List.of() : List.copyOf(models);
    }

    public static AiPricing none() {
        return new AiPricing(List.of());
    }

    /** Estimated cost in USD, or empty when the model is unknown (e.g. the test stub). */
    public Optional<BigDecimal> cost(String model, int tokensIn, int tokensOut) {
        return priceOf(model).map(p -> p.inputUsdPerMillion().multiply(BigDecimal.valueOf(tokensIn))
                .add(p.outputUsdPerMillion().multiply(BigDecimal.valueOf(tokensOut)))
                .divide(MILLION, new MathContext(12, RoundingMode.HALF_UP))
                .setScale(8, RoundingMode.HALF_UP));
    }

    Optional<ModelPrice> priceOf(String model) {
        if (model == null) {
            return Optional.empty();
        }
        String name = model.startsWith("models/") ? model.substring("models/".length()) : model;
        return models.stream()
                .filter(p -> name.equals(p.model()) || name.startsWith(p.model() + "-"))
                .max(Comparator.comparingInt(p -> p.model().length()));   // most specific entry wins
    }
}
