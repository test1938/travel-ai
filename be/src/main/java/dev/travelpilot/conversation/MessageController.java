package dev.travelpilot.conversation;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Chat on a trip: ask questions or change the plan. Owner via X-Guest-Id until guest tokens (T6.01). */
@RestController
@RequestMapping("/api/v1/trips/{id}/messages")
public class MessageController {

    private final ChatService chat;

    public MessageController(ChatService chat) {
        this.chat = chat;
    }

    @PostMapping
    public ChatService.ChatReply send(@PathVariable UUID id,
            @RequestHeader(value = "X-Guest-Id", defaultValue = "anonymous") @Pattern(regexp = "[A-Za-z0-9-]{1,64}") String guestId,
            @Valid @RequestBody MessageRequest request) {
        return chat.handle(id, guestId, request.message().strip());
    }

    @GetMapping
    public List<ChatMessage> history(@PathVariable UUID id,
            @RequestHeader(value = "X-Guest-Id", defaultValue = "anonymous") @Pattern(regexp = "[A-Za-z0-9-]{1,64}") String guestId) {
        return chat.history(id, guestId);
    }

    public record MessageRequest(@NotBlank @Size(max = 1000) String message) {}
}
