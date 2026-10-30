package dev.travelpilot.common.ai;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Developer-only cost report from ai_call_logs; the bean does not exist outside the "dev" profile. */
@Profile("dev")
@RestController
public class AiCostController {

    private final JdbcAiCallLogStore store;

    public AiCostController(JdbcAiCallLogStore store) {
        this.store = store;
    }

    @GetMapping("/internal/ai-costs")
    public AiCostReport costs(@RequestParam(defaultValue = "7") @Min(1) @Max(90) int days) {
        return store.report(days);
    }
}
