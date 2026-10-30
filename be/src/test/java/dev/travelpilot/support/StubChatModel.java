package dev.travelpilot.support;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

/**
 * Offline ChatModel for tests: each call consumes the next queued reply (text, exception or delayed text).
 * Received prompts are recorded so tests can assert on what was sent to the model.
 */
public class StubChatModel implements ChatModel {

    private sealed interface Reply permits Text, Failure {}

    private record Text(String text, Duration delay) implements Reply {}

    private record Failure(RuntimeException error) implements Reply {}

    private final Deque<Reply> replies = new ArrayDeque<>();
    private final List<Prompt> prompts = new ArrayList<>();

    public synchronized StubChatModel reply(String... texts) {
        for (String t : texts) {
            replies.add(new Text(t, Duration.ZERO));
        }
        return this;
    }

    public synchronized StubChatModel replyAfter(Duration delay, String text) {
        replies.add(new Text(text, delay));
        return this;
    }

    public synchronized StubChatModel fail(RuntimeException error) {
        replies.add(new Failure(error));
        return this;
    }

    public synchronized List<Prompt> prompts() {
        return List.copyOf(prompts);
    }

    public synchronized void reset() {
        replies.clear();
        prompts.clear();
    }

    @Override
    public ChatResponse call(Prompt prompt) {
        Reply next;
        synchronized (this) {
            prompts.add(prompt);
            next = replies.poll();
        }
        if (next == null) {
            throw new IllegalStateException("StubChatModel: no reply queued");
        }
        return switch (next) {
            case Failure f -> throw f.error();
            case Text t -> {
                sleep(t.delay());
                yield new ChatResponse(List.of(new Generation(new AssistantMessage(t.text()))));
            }
        };
    }

    private static void sleep(Duration d) {
        if (d.isZero()) {
            return;
        }
        try {
            Thread.sleep(d);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
