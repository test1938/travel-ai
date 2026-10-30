package dev.travelpilot.conversation;

import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** Stores the chat of a trip; the full history is for the UI, {@link #window} for the model. */
@Service
public class ConversationService {

    /** Messages sent to the model as context (bounded memory, plan/02-architecture.md). */
    public static final int WINDOW = 6;

    private final ConversationRepository repository;

    public ConversationService(ConversationRepository repository) {
        this.repository = repository;
    }

    public void addUserMessage(UUID tripId, String content) {
        repository.append(repository.getOrCreate(tripId), ChatMessage.Role.USER, content, null, null);
    }

    public void addAssistantMessage(UUID tripId, String content, Intent intent, Integer tripVersion) {
        repository.append(repository.getOrCreate(tripId), ChatMessage.Role.ASSISTANT, content, intent, tripVersion);
    }

    public List<ChatMessage> history(UUID tripId) {
        return repository.history(tripId);
    }

    public List<ChatMessage> window(UUID tripId) {
        return repository.recent(tripId, WINDOW);
    }
}
