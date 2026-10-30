package dev.travelpilot.playground;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Playground endpoint from the walking skeleton (T0.07): free chat without trip context or citations, so it only
 * exists in the {@code dev} profile (T6.04).
 */
@Profile("dev")
@RestController
@RequestMapping("/api/v1/ai")
public class AiChatController {

    private final AiChatService service;

    public AiChatController(AiChatService service) {
        this.service = service;
    }

    @PostMapping("/chat")
    public AiChatResponse chat(@Valid @RequestBody ChatRequest request) {
        return service.chat(request.message());
    }

    public record ChatRequest(@NotBlank @Size(max = 2000) String message) {}
}
