package dev.travelpilot.catalog;

import dev.travelpilot.common.domain.BestTime;
import dev.travelpilot.common.domain.Destination;
import dev.travelpilot.common.domain.PlaceTag;
import dev.travelpilot.common.domain.PlaceType;
import dev.travelpilot.common.domain.PriceLevel;
import java.sql.Array;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/** SQL access to the {@code places} table. Tags are stored with their dataset codes (e.g. "THEME-PARK"). */
@Repository
public class PlaceRepository {

    private static final String UPSERT = """
            INSERT INTO places (id, destination, type, name, name_en, description, curated, osm_id, tags,
                est_cost_vnd, child_cost_vnd, price_level, price_note, price_source_url, verify_at, duration_min,
                child_min_age, indoor, best_time, opening_hours, lat, lon, popularity, day_trip, source_url,
                estimated_fields, review_fields, field_sources, updated_at)
            VALUES (:id, :destination, :type, :name, :nameEn, :description, :curated, :osmId, :tags,
                :estCostVnd, :childCostVnd, :priceLevel, :priceNote, :priceSourceUrl, :verifyAt, :durationMin,
                :childMinAge, :indoor, :bestTime, :openingHours, :lat, :lon, :popularity, :dayTrip, :sourceUrl,
                :estimatedFields, :reviewFields, CAST(:fieldSources AS jsonb), now())
            ON CONFLICT (id) DO UPDATE SET
                destination = EXCLUDED.destination, type = EXCLUDED.type, name = EXCLUDED.name,
                name_en = EXCLUDED.name_en, description = EXCLUDED.description, curated = EXCLUDED.curated,
                osm_id = EXCLUDED.osm_id, tags = EXCLUDED.tags, est_cost_vnd = EXCLUDED.est_cost_vnd,
                child_cost_vnd = EXCLUDED.child_cost_vnd, price_level = EXCLUDED.price_level,
                price_note = EXCLUDED.price_note, price_source_url = EXCLUDED.price_source_url,
                verify_at = EXCLUDED.verify_at, duration_min = EXCLUDED.duration_min,
                child_min_age = EXCLUDED.child_min_age, indoor = EXCLUDED.indoor, best_time = EXCLUDED.best_time,
                opening_hours = EXCLUDED.opening_hours, lat = EXCLUDED.lat, lon = EXCLUDED.lon,
                popularity = EXCLUDED.popularity, day_trip = EXCLUDED.day_trip, source_url = EXCLUDED.source_url,
                estimated_fields = EXCLUDED.estimated_fields, review_fields = EXCLUDED.review_fields,
                field_sources = EXCLUDED.field_sources, updated_at = now()
            """;

    private static final TypeReference<Map<String, String>> STRING_MAP = new TypeReference<>() {};

    private final JdbcClient jdbc;
    private final JsonMapper json;
    private final RowMapper<Place> rowMapper = this::mapRow;

    public PlaceRepository(JdbcClient jdbc, JsonMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Transactional
    public void upsertAll(Collection<Place> places) {
        places.forEach(this::upsert);
    }

    public void upsert(Place p) {
        jdbc.sql(UPSERT)
                .param("id", p.id())
                .param("destination", p.destination().name())
                .param("type", p.type().name())
                .param("name", p.name())
                .param("nameEn", p.nameEn())
                .param("description", p.description())
                .param("curated", p.curated())
                .param("osmId", p.osmId())
                .param("tags", p.tags().stream().map(PlaceTag::code).toArray(String[]::new))
                .param("estCostVnd", p.estCostVnd())
                .param("childCostVnd", p.childCostVnd())
                .param("priceLevel", p.priceLevel().name())
                .param("priceNote", p.priceNote())
                .param("priceSourceUrl", p.priceSourceUrl())
                .param("verifyAt", p.verifyAt() == null ? null : Date.valueOf(p.verifyAt()))
                .param("durationMin", p.durationMin())
                .param("childMinAge", p.childMinAge())
                .param("indoor", p.indoor())
                .param("bestTime", p.bestTime().name())
                .param("openingHours", p.openingHours())
                .param("lat", p.lat())
                .param("lon", p.lon())
                .param("popularity", p.popularity())
                .param("dayTrip", p.dayTrip())
                .param("sourceUrl", p.sourceUrl())
                .param("estimatedFields", p.estimatedFields().toArray(String[]::new))
                .param("reviewFields", p.reviewFields().toArray(String[]::new))
                .param("fieldSources", json.writeValueAsString(p.fieldSources()))
                .update();
    }

    public long count() {
        return jdbc.sql("SELECT count(*) FROM places").query(Long.class).single();
    }

    public Optional<Place> findById(String id) {
        return jdbc.sql("SELECT * FROM places WHERE id = :id").param("id", id).query(rowMapper).optional();
    }

    /** Places with the given ids, in the order of {@code ids}; unknown ids are skipped. */
    public List<Place> findByIds(Collection<String> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        List<Place> found = jdbc.sql("SELECT * FROM places WHERE id = ANY(:ids)")
                .param("ids", ids.toArray(String[]::new))
                .query(rowMapper).list();
        Map<String, Place> byId = new LinkedHashMap<>();
        found.forEach(p -> byId.put(p.id(), p));
        return ids.stream().map(byId::get).filter(p -> p != null).toList();
    }

    public List<Place> findByDestination(Destination destination) {
        return jdbc.sql("SELECT * FROM places WHERE destination = :d ORDER BY type, popularity DESC, id")
                .param("d", destination.name())
                .query(rowMapper).list();
    }

    public List<Place> find(PlaceQuery q) {
        Where where = where(q);
        return jdbc.sql("SELECT * FROM places" + where.sql()
                        + " ORDER BY destination, type, popularity DESC, id LIMIT :limit OFFSET :offset")
                .params(where.params())
                .param("limit", q.size())
                .param("offset", q.offset())
                .query(rowMapper).list();
    }

    public long count(PlaceQuery q) {
        Where where = where(q);
        return jdbc.sql("SELECT count(*) FROM places" + where.sql()).params(where.params())
                .query(Long.class).single();
    }

    private record Where(String sql, Map<String, Object> params) {}

    private static Where where(PlaceQuery q) {
        StringBuilder sql = new StringBuilder(" WHERE 1 = 1");
        Map<String, Object> params = new LinkedHashMap<>();
        if (q.destination() != null) {
            sql.append(" AND destination = :destination");
            params.put("destination", q.destination().name());
        }
        if (q.type() != null) {
            sql.append(" AND type = :type");
            params.put("type", q.type().name());
        }
        if (q.tag() != null) {
            sql.append(" AND :tag = ANY(tags)");
            params.put("tag", q.tag().code());
        }
        return new Where(sql.toString(), params);
    }

    private Place mapRow(ResultSet rs, int rowNum) throws SQLException {
        Date verifyAt = rs.getDate("verify_at");
        return new Place(
                rs.getString("id"),
                Destination.valueOf(rs.getString("destination")),
                PlaceType.valueOf(rs.getString("type")),
                rs.getString("name"),
                rs.getString("name_en"),
                rs.getString("description"),
                rs.getBoolean("curated"),
                rs.getString("osm_id"),
                Arrays.stream(strings(rs.getArray("tags"))).map(PlaceTag::fromCode).toList(),
                rs.getLong("est_cost_vnd"),
                rs.getLong("child_cost_vnd"),
                PriceLevel.valueOf(rs.getString("price_level")),
                rs.getString("price_note"),
                rs.getString("price_source_url"),
                verifyAt == null ? null : verifyAt.toLocalDate(),
                rs.getInt("duration_min"),
                rs.getInt("child_min_age"),
                rs.getBoolean("indoor"),
                BestTime.valueOf(rs.getString("best_time")),
                rs.getString("opening_hours"),
                rs.getDouble("lat"),
                rs.getDouble("lon"),
                rs.getDouble("popularity"),
                rs.getBoolean("day_trip"),
                rs.getString("source_url"),
                List.of(strings(rs.getArray("estimated_fields"))),
                List.of(strings(rs.getArray("review_fields"))),
                json.readValue(rs.getString("field_sources"), STRING_MAP));
    }

    private static String[] strings(Array array) throws SQLException {
        return array == null ? new String[0] : (String[]) array.getArray();
    }
}
