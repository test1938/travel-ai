package dev.travelpilot.conversation;

import dev.travelpilot.security.Guest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Chat on a trip: ask questions or change the plan. Only the trip owner (guest token) may chat. */
@RestController
@RequestMapping("/api/v1/trips/{id}/messages")
public class MessageController {

    private final ChatService chat;

    public MessageController(ChatService chat) {
        this.chat = chat;
    }

    @PostMapping
    public ChatService.ChatReply send(@PathVariable UUID id,
            Guest guest,
            @Valid @RequestBody MessageRequest request) {
        return chat.handle(id, guest.id(), request.message().strip());
    }

    @GetMapping
    public List<ChatMessage> history(@PathVariable UUID id,
            Guest guest) {
        return chat.history(id, guest.id());
    }

    public record MessageRequest(@NotBlank @Size(max = 1000) String message) {}
}
