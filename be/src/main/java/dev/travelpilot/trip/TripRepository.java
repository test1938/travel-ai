package dev.travelpilot.trip;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.json.JsonMapper;

/** SQL for trips and their immutable versions; plans and requests are stored as JSONB. */
@Repository
public class TripRepository {

    /** Trip header row. */
    public record TripRow(UUID id, String guestId, TripRequest request, int currentVersion, OffsetDateTime createdAt) {}

    /** Generation facts stored next to each version (eval and cost data). */
    public record VersionFacts(String changeSummary, String promptVersion, String model, int tokensIn,
            int tokensOut, long latencyMs, boolean repaired, boolean fallback) {}

    private final JdbcClient jdbc;
    private final JsonMapper json;

    public TripRepository(JdbcClient jdbc, JsonMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    public void insertTrip(UUID id, String guestId, TripRequest request, int currentVersion) {
        jdbc.sql("""
                INSERT INTO trips (id, guest_id, destination, request_json, current_version)
                VALUES (:id, :guest, :dest, CAST(:request AS jsonb), :version)
                """)
                .param("id", id)
                .param("guest", guestId)
                .param("dest", request.destination().name())
                .param("request", json.writeValueAsString(request))
                .param("version", currentVersion)
                .update();
    }

    public void insertVersion(UUID tripId, int version, TripPlan plan, TripRequest request, VersionFacts facts) {
        jdbc.sql("""
                INSERT INTO trip_versions (trip_id, version, plan_json, request_json, change_summary, prompt_version,
                    model, tokens_in, tokens_out, latency_ms, repaired, fallback)
                VALUES (:trip, :version, CAST(:plan AS jsonb), CAST(:request AS jsonb), :summary, :prompt, :model,
                    :in, :out, :latency, :repaired, :fallback)
                """)
                .param("trip", tripId)
                .param("version", version)
                .param("plan", json.writeValueAsString(plan))
                .param("request", json.writeValueAsString(request))
                .param("summary", facts.changeSummary())
                .param("prompt", facts.promptVersion())
                .param("model", facts.model())
                .param("in", facts.tokensIn())
                .param("out", facts.tokensOut())
                .param("latency", facts.latencyMs())
                .param("repaired", facts.repaired())
                .param("fallback", facts.fallback())
                .update();
    }

    public void updateCurrent(UUID tripId, int version, TripRequest request) {
        jdbc.sql("""
                UPDATE trips SET current_version = :version, request_json = CAST(:request AS jsonb), updated_at = now()
                WHERE id = :id
                """)
                .param("id", tripId)
                .param("version", version)
                .param("request", json.writeValueAsString(request))
                .update();
    }

    public Optional<TripRow> findTrip(UUID id) {
        return jdbc.sql("SELECT id, guest_id, request_json, current_version, created_at FROM trips WHERE id = :id")
                .param("id", id)
                .query((rs, n) -> new TripRow(rs.getObject("id", UUID.class), rs.getString("guest_id"),
                        json.readValue(rs.getString("request_json"), TripRequest.class),
                        rs.getInt("current_version"), rs.getObject("created_at", OffsetDateTime.class)))
                .optional();
    }

    public Optional<TripPlan> findVersion(UUID tripId, int version) {
        return jdbc.sql("SELECT plan_json FROM trip_versions WHERE trip_id = :id AND version = :version")
                .param("id", tripId)
                .param("version", version)
                .query((rs, n) -> json.readValue(rs.getString("plan_json"), TripPlan.class))
                .optional();
    }
}
