package dev.travelpilot.conversation;

import dev.travelpilot.trip.TripPlan;
import dev.travelpilot.trip.TripRepository;
import dev.travelpilot.trip.TripRequest;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** In-memory replacements for the JDBC repositories, so a whole chat can run without a database. */
final class InMemoryRepositories {

    private InMemoryRepositories() {}

    static final class Trips extends TripRepository {
        final Map<UUID, TripRow> trips = new HashMap<>();
        final Map<String, TripPlan> versions = new HashMap<>();
        final Map<String, VersionFacts> facts = new HashMap<>();

        Trips() {
            super(null, null);
        }

        @Override
        public void insertTrip(UUID id, String guestId, TripRequest request, int currentVersion) {
            trips.put(id, new TripRow(id, guestId, request, currentVersion, OffsetDateTime.now()));
        }

        @Override
        public void insertVersion(UUID tripId, int version, TripPlan plan, TripRequest request, VersionFacts f) {
            versions.put(tripId + "/" + version, plan);
            facts.put(tripId + "/" + version, f);
        }

        @Override
        public void updateCurrent(UUID tripId, int version, TripRequest request) {
            TripRow old = trips.get(tripId);
            trips.put(tripId, new TripRow(tripId, old.guestId(), request, version, old.createdAt()));
        }

        @Override
        public Optional<TripRow> findTrip(UUID id) {
            return Optional.ofNullable(trips.get(id));
        }

        @Override
        public Optional<TripPlan> findVersion(UUID tripId, int version) {
            return Optional.ofNullable(versions.get(tripId + "/" + version));
        }
    }

    static final class Conversations extends ConversationRepository {
        final Map<UUID, UUID> byTrip = new HashMap<>();
        final Map<UUID, List<ChatMessage>> messages = new HashMap<>();
        private long nextId = 1;

        Conversations() {
            super(null);
        }

        @Override
        public Optional<UUID> findByTrip(UUID tripId) {
            return Optional.ofNullable(byTrip.get(tripId));
        }

        @Override
        public UUID getOrCreate(UUID tripId) {
            return byTrip.computeIfAbsent(tripId, t -> UUID.randomUUID());
        }

        @Override
        public long append(UUID conversationId, ChatMessage.Role role, String content, Intent intent, Integer version) {
            long id = nextId++;
            messages.computeIfAbsent(conversationId, c -> new ArrayList<>())
                    .add(new ChatMessage(id, role, content, intent, version, OffsetDateTime.now()));
            return id;
        }

        @Override
        public List<ChatMessage> history(UUID tripId) {
            UUID conv = byTrip.get(tripId);
            return conv == null ? List.of() : List.copyOf(messages.getOrDefault(conv, List.of()));
        }

        @Override
        public List<ChatMessage> recent(UUID tripId, int limit) {
            List<ChatMessage> all = history(tripId);
            return all.subList(Math.max(0, all.size() - limit), all.size());
        }
    }
}
