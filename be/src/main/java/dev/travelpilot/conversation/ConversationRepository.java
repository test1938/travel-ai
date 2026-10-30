package dev.travelpilot.conversation;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** SQL for conversations (one per trip) and their messages. */
@Repository
public class ConversationRepository {

    private static final RowMapper<ChatMessage> MESSAGE = (rs, n) -> new ChatMessage(
            rs.getLong("id"),
            ChatMessage.Role.valueOf(rs.getString("role")),
            rs.getString("content"),
            rs.getString("intent") == null ? null : Intent.valueOf(rs.getString("intent")),
            (Integer) rs.getObject("trip_version"),
            rs.getObject("created_at", OffsetDateTime.class));

    private final JdbcClient jdbc;

    public ConversationRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<UUID> findByTrip(UUID tripId) {
        return jdbc.sql("SELECT id FROM conversations WHERE trip_id = :trip").param("trip", tripId)
                .query(UUID.class).optional();
    }

    /** Creates the trip's conversation if missing and returns its id (safe under concurrent calls). */
    public UUID getOrCreate(UUID tripId) {
        jdbc.sql("INSERT INTO conversations (id, trip_id) VALUES (:id, :trip) ON CONFLICT (trip_id) DO NOTHING")
                .param("id", UUID.randomUUID())
                .param("trip", tripId)
                .update();
        return findByTrip(tripId).orElseThrow();
    }

    public long append(UUID conversationId, ChatMessage.Role role, String content, Intent intent, Integer version) {
        return jdbc.sql("""
                INSERT INTO messages (conversation_id, role, content, intent, trip_version)
                VALUES (:conv, :role, :content, :intent, :version) RETURNING id
                """)
                .param("conv", conversationId)
                .param("role", role.name())
                .param("content", content)
                .param("intent", intent == null ? null : intent.name())
                .param("version", version)
                .query(Long.class).single();
    }

    /** All messages of the trip, oldest first. */
    public List<ChatMessage> history(UUID tripId) {
        return jdbc.sql("""
                SELECT m.* FROM messages m JOIN conversations c ON c.id = m.conversation_id
                WHERE c.trip_id = :trip ORDER BY m.id
                """).param("trip", tripId).query(MESSAGE).list();
    }

    /** The last {@code limit} messages, oldest first. */
    public List<ChatMessage> recent(UUID tripId, int limit) {
        List<ChatMessage> newestFirst = jdbc.sql("""
                SELECT m.* FROM messages m JOIN conversations c ON c.id = m.conversation_id
                WHERE c.trip_id = :trip ORDER BY m.id DESC LIMIT :limit
                """).param("trip", tripId).param("limit", limit).query(MESSAGE).list();
        return newestFirst.reversed();
    }
}
