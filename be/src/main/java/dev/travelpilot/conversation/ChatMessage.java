package dev.travelpilot.conversation;

import java.time.OffsetDateTime;

/** One stored chat message; {@code intent} and {@code tripVersion} are set on assistant replies. */
public record ChatMessage(long id, Role role, String content, Intent intent, Integer tripVersion,
        OffsetDateTime createdAt) {

    public enum Role { USER, ASSISTANT }
}
