package dev.travelpilot.playground;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Playground endpoint for the walking skeleton (T0.07). Removed or restricted in Phase 6. */
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
