package dev.travelpilot.ask;

import dev.travelpilot.common.domain.Destination;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Grounded Q&A about one destination, with citations. */
@RestController
@RequestMapping("/api/v1/ask")
public class AskController {

    private final AskService service;

    public AskController(AskService service) {
        this.service = service;
    }

    @PostMapping
    public AskResponse ask(@Valid @RequestBody AskRequest request) {
        return service.ask(request.destination(), request.question().strip());
    }

    public record AskRequest(@NotNull Destination destination, @NotBlank @Size(max = 1000) String question) {}
}
