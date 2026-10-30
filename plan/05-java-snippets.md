# Mẫu code (phác thảo, đối chiếu API Spring AI 2.0.2 trước khi dùng)

> Boot 4 dùng **Jackson 3**: `ObjectMapper`/`JsonMapper` và `TypeReference` ở package `tools.jackson.databind` / `tools.jackson.core.type`; các annotation (`@JsonProperty`, `@JsonIgnoreProperties`…) vẫn ở `com.fasterxml.jackson.annotation`. Stub test thật đã có ở `be/src/test/java/dev/travelpilot/support/`.

## 1. PlaceTag: enum có giá trị chứa gạch ngang

```java
public enum PlaceTag {
    BEACH, NATURE, MOUNTAIN, ISLAND, CAVE, RIVER, WATER, CULTURE, HISTORY, MUSEUM, TEMPLE, ART,
    LANDMARK, VILLAGE, CRAFT, FAMILY, KIDS,
    @JsonProperty("THEME-PARK") THEME_PARK,
    @JsonProperty("CABLE-CAR") CABLE_CAR,
    SHOW, FOOD, SEAFOOD,
    @JsonProperty("STREET-FOOD") STREET_FOOD,
    LOCAL, CAFE, MARKET, SHOPPING, NIGHTLIFE, NIGHT, PHOTO, ROMANTIC, RELAX, SPA, SIGHTSEEING,
    ADVENTURE, HIKING, KAYAK, BOAT;

    /** Giá trị đúng như trong JSON và trong cột text[] của DB. */
    public String code() { return name().replace('_', '-'); }

    public static PlaceTag fromCode(String code) { return valueOf(code.replace('-', '_')); }
}
```

Test bắt buộc: đọc cả 3 catalog bằng `ObjectMapper` mà không lỗi, và `fromCode(t.code()) == t` với mọi giá trị.

## 2. DTO đọc catalog và SeedLoader idempotent

```java
@JsonIgnoreProperties(ignoreUnknown = false)   // bật strict để phát hiện ngay khi pipeline thêm trường
public record PlaceJson(
        String id, Destination destination, PlaceType type, String name, String nameEn, String description,
        Boolean curated, String osmId, Set<PlaceTag> tags, Long estCostVnd, Long childCostVnd,
        PriceLevel priceLevel, String priceNote, String priceSourceUrl, LocalDate verifyAt,
        Integer durationMin, Integer childMinAge, Boolean indoor, BestTime bestTime, String openingHours,
        Double lat, Double lon, Double popularity, Boolean dayTrip, String sourceUrl,
        List<String> estimatedFields, List<String> reviewFields, Map<String, String> fieldSources) {}

@Component
@RequiredArgsConstructor   // hoặc viết constructor
class SeedLoader implements ApplicationRunner {
    private final ObjectMapper mapper;
    private final PlaceRepository repo;
    private final ResourcePatternResolver resolver;

    @Override public void run(ApplicationArguments args) throws IOException {
        for (Resource r : resolver.getResources("classpath:seed/catalog_*.json")) {
            List<PlaceJson> places = mapper.readValue(r.getInputStream(), new TypeReference<>() {});
            places.forEach(repo::upsert);            // INSERT ... ON CONFLICT (id) DO UPDATE SET ...
            log.info("seeded {} places from {}", places.size(), r.getFilename());
        }
    }
}
```

`upsert` dùng `JdbcClient`:
- `tags` truyền dạng `String[]` các `code()`.
- `field_sources` truyền `mapper.writeValueAsString(...)` kèm `::jsonb`.

Chạy SeedLoader hai lần thì số dòng không đổi.

## 3. Ingestion knowledge vào PgVectorStore

```java
record KnowledgeChunk(String docId, Destination destination, String docType, String lang, String title,
                      String text, List<String> placeIds, String source, String sourceUrl, String license,
                      String retrievedAt, String translatedFrom, String originalTitle) {}

public IngestReport ingest() throws IOException {
    for (Resource r : resolver.getResources("classpath:seed/knowledge_*.jsonl")) {
        List<KnowledgeChunk> chunks = readJsonl(r);                 // 1 dòng = 1 chunk; dòng lỗi → exception nêu số dòng
        Destination dest = chunks.get(0).destination();
        vectorStore.delete(new FilterExpressionBuilder().eq("destination", dest.name()).build());
        List<Document> docs = chunks.stream().map(c -> {
            Map<String, Object> meta = new HashMap<>();
            meta.put("docId", c.docId());
            meta.put("destination", c.destination().name());
            meta.put("docType", c.docType());
            meta.put("title", c.title());
            meta.put("placeIds", c.placeIds());
            if (c.placeIds().size() == 1) meta.put("placeId", c.placeIds().get(0));
            putIfNotNull(meta, "source", c.source());
            putIfNotNull(meta, "sourceUrl", c.sourceUrl());
            putIfNotNull(meta, "license", c.license());
            putIfNotNull(meta, "retrievedAt", c.retrievedAt());
            putIfNotNull(meta, "translatedFrom", c.translatedFrom());
            String id = UUID.nameUUIDFromBytes(c.docId().getBytes(StandardCharsets.UTF_8)).toString();
            return new Document(id, c.title() + "\n" + c.text(), meta);
        }).toList();
        for (int i = 0; i < docs.size(); i += 50)                     // batch 50, tránh request embedding quá lớn
            vectorStore.add(docs.subList(i, Math.min(i + 50, docs.size())));
    }
}
```

Nếu `vectorStore.delete(Filter.Expression)` không có trong bản patch đang dùng, dùng `JdbcClient`: `DELETE FROM vector_store WHERE metadata->>'destination' = ?`.

## 4. BudgetCalculator

```java
public final class BudgetCalculator {
    public static long itemCost(Place p, int adults, List<Integer> childrenAges) {
        long kids = childrenAges.stream().filter(a -> a >= p.childMinAge()).count();
        return p.estCostVnd() * adults + p.childCostVnd() * kids;
    }

    public static Budget total(List<PlanItem> items, long limit) {
        Map<PlaceType, Long> byType = new EnumMap<>(PlaceType.class);
        items.forEach(i -> byType.merge(i.type(), i.costVnd(), Long::sum));
        long total = byType.values().stream().mapToLong(Long::longValue).sum();
        return new Budget(total, limit, byType, total > limit);
    }
}
// Test tay: Bà Nà (1.000.000 / 800.000), 2 người lớn + bé 6 tuổi → 2.800.000
```

## 5. StubChatModel cho test

```java
public class StubChatModel implements ChatModel {
    private final Deque<String> replies = new ArrayDeque<>();
    public StubChatModel reply(String... r) { replies.addAll(List.of(r)); return this; }

    @Override public ChatResponse call(Prompt prompt) {
        if (replies.isEmpty()) throw new IllegalStateException("StubChatModel: no reply queued");
        return new ChatResponse(List.of(new Generation(new AssistantMessage(replies.pop()))));
    }
}

@TestConfiguration
class StubAiConfig {
    @Bean @Primary StubChatModel stubChatModel() { return new StubChatModel(); }
    @Bean @Primary EmbeddingModel stubEmbeddingModel() { return new StubEmbeddingModel(768); }  // = dimensions
}
```

`StubEmbeddingModel` sinh vector tất định từ `text.hashCode()` (seed `Random`), rồi chuẩn hóa về độ dài 1.

## 6. Gọi planner, lấy cả entity lẫn usage

```java
var res = chatClient.prompt()
        .system(s -> s.text(prompts.load("planner", version)).params(systemParams))
        .user(u -> u.text(prompts.loadUser("planner", version)).params(userParams))
        .options(ChatOptions.builder().temperature(0.3).build())
        .call()
        .responseEntity(TripPlanDraft.class);
TripPlanDraft draft = res.entity();
Usage usage = res.response().getMetadata().getUsage();
aiCallLogger.log(Operation.PLAN, version, usage, latency, retrievedIds);
```
