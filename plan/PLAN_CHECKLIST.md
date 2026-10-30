# TravelPilot AI: checklist kế hoạch code

> **Dành cho agent:**
> - Đọc `plan/00-overview.md` trước khi bắt đầu. Các tài liệu kỹ thuật nằm cùng thư mục `plan/` (01–05).
> - Làm **từng task theo thứ tự**. Chỉ đánh `[x]` khi đã chạy đúng lệnh hoặc kiểm tra ở dòng **DoD** và kết quả đạt.
> - Task bị chặn: ghi `> BLOCKED: lý do` ngay dưới task rồi chuyển sang task kế tiếp không phụ thuộc nó.
> - Không đổi quyết định kiến trúc khi chưa hỏi người dùng. Không sửa file trong `data/`.
> - **Theo luật code, pattern và commit trong `plan/04-conventions.md`** (bắt buộc): package theo tính năng, controller mỏng, pattern đúng chỗ, **commit nhỏ sau mỗi task hoặc mỗi bước chạy xanh**, message `feat(scope): Txx.xx ...`.
> - Sau mỗi task backend, `cd be && ./mvnw test` phải xanh. Sau mỗi task frontend, `cd fe && npm run lint && npm run build` phải xanh.
>
> **Ghi chú cho người dùng:**
> - Mục đánh dấu 👤 là việc bạn tự làm (agent không làm được hoặc không nên làm): tạo tài khoản, API key, quay video, test người dùng, CV.
> - Nếu thiếu thời gian, xem mục "Cắt scope" ở cuối file.

**Cấu trúc repo:** gốc chỉ có 4 thư mục `data/` (dữ liệu, chỉ đọc), `plan/` (kế hoạch + tài liệu), `be/` (Spring Boot), `fe/` (React). File chung ở gốc: `README.md`, `docker-compose.yml`, `compose.prod.yml`, `.env.example`, `.gitignore`, `.github/`.

**Đã xong trước khi bắt đầu (không làm lại):**
- [x] Dataset trong `data/`: catalog 3 điểm đến (101 địa điểm), knowledge 401 chunk tiếng Việt, golden set 24 câu, pool OSM, schema, ATTRIBUTION.
- [x] Chốt 3 điểm đến `HANOI`, `DANANG`, `QUANGNINH` (lý do tóm tắt ở `plan/01-dataset.md` và sẽ ghi lại thành ADR-009 ở T0.11).

---

## Phase 0: Khung dự án chạy xuyên suốt

- [x] **T0.01 Repo và cấu trúc thư mục**
  - Giữ đúng 4 thư mục `data/ plan/ be/ fe/` (đã có sẵn), tạo thêm `plan/adr/`. Xem `plan/02-architecture.md` › Cấu trúc repo.
  - Thêm ở gốc: `.gitignore` (Java, Node, `.env`, `target/`, `node_modules/`, `dist/`), `LICENSE` (MIT), và `README.md` stub 3 dòng.
  - `git init`, commit đầu tiên. 👤 Tạo repo GitHub và push.
  - **DoD:** `git status` sạch; cây thư mục đúng như mô tả.

- [ ] **T0.02 Docker Compose cho Postgres + pgvector**
  - `docker-compose.yml`:
    - Service `db` dùng image `pgvector/pgvector:pg16`, DB `travelpilot`, user/pass `tp`/`tp`.
    - Port 5432, volume `pgdata`, healthcheck `pg_isready`.
  - **DoD:** `docker compose up -d` → `docker compose ps` báo healthy; `psql -c "create extension if not exists vector"` chạy OK.
  > CHỜ KIỂM TRA: file đã viết; máy dev hiện chưa có Docker nên chưa chạy được DoD. 👤 Cài Docker Desktop rồi chạy `docker compose up -d`.

- [ ] **T0.03 Khởi tạo Spring Boot**
  - Java 21, Boot 4.1.2, Maven wrapper, group `dev.travelpilot`, artifact `travelpilot`, trong `be/`. Dependencies: WebMVC, Validation, JDBC, Flyway, PostgreSQL, Actuator, Testcontainers.
  - Thêm `spring-ai-bom` 2.0.2, `spring-ai-starter-model-google-genai`, `spring-ai-starter-model-google-genai-embedding`, `spring-ai-starter-vector-store-pgvector`.
  - Trong `be/pom.xml`, map `../data` vào classpath: `catalog_*.json` + `knowledge_*.jsonl` → `seed/`, `golden_set.json` → test `eval/` (đoạn XML ở `plan/01-dataset.md` §1). **Không copy dữ liệu vào `be/`.**
  - Provider đã chọn: **Google Gemini**. 👤 Tạo API key ở Google AI Studio, điền `AI_API_KEY` vào `.env`, đặt giới hạn chi tiêu.
  - **DoD:** `./mvnw spring-boot:run` → `GET /actuator/health` trả `UP`, kết nối DB OK; `./mvnw package` xong thì `target/classes/seed/` có 6 file dữ liệu.
  > MỘT PHẦN: build, dependency, map `../data` → `classpath:seed/` đã kiểm (`DataResourcesTest` xanh). Chưa chạy `/actuator/health` vì chưa có DB (Docker) và key. Ghi chú máy công ty: Nexus thiếu Spring AI, dùng `mvnw -s %USERPROFILE%\.m2\settings-central.xml` (Maven Central qua proxy).

- [x] **T0.04 Cấu hình và secret**
  - Tạo `application.yml`, `application-dev.yml`, `application-prod.yml`.
  - Tạo `.env.example` với `AI_API_KEY, DB_URL, DB_USER, DB_PASSWORD, GUEST_TOKEN_SECRET`; `.env` nằm trong `.gitignore`.
  - Khai báo block `travelpilot.*` (scoring weights, `planner.max-repairs: 2`, `planner.timeout-seconds: 40`, `rag.top-k: 5`, `rag.threshold: 0.45`) kèm `@ConfigurationProperties` + `@Validated`.
  - Cấu hình `spring.ai.vectorstore.pgvector`: `index-type: HNSW`, `distance-type: COSINE_DISTANCE`, `dimensions: <khớp model>`, `initialize-schema: true` (chỉ dev).
  - **DoD:** `git grep -i "sk-"` không ra kết quả; app đọc key từ env; đổi một trọng số trong yml thì bean nhận đúng giá trị (unit test).

- [ ] **T0.05 Flyway V1**
  - Tạo `V1__init.sql` với `create extension if not exists vector;`.
  - **DoD:** start app → có bảng `flyway_schema_history` với V1 success.
  > CHỜ KIỂM TRA: `FlywayMigrationTest` (Testcontainers) đã viết, đang bị skip vì máy chưa có Docker.

- [x] **T0.06 Xử lý lỗi chung**
  - Tạo `ErrorCode` (xem `plan/02-architecture.md`), `ApiException`, `GlobalExceptionHandler` trả `ProblemDetail` kèm `code` và `requestId`.
  - Map lỗi validation → 400, lỗi provider → 502 `LLM_ERROR`, timeout → 504 `LLM_TIMEOUT`.
  - Cấu hình timeout cho HTTP client của provider qua `RestClientCustomizer`.
  - **DoD:** test MockMvc: body rỗng → 400 có `code = VALIDATION_FAILED`.

- [ ] **T0.07 Endpoint thử `POST /api/v1/ai/chat`**
  - `ChatRequest(@NotBlank @Size(max=2000) String message)` → `{answer, model, latencyMs}`.
  - Thêm file `be/http/chat.http`.
  - **DoD:** gọi thật với key trả về câu trả lời. (Endpoint này sẽ xóa hoặc ẩn ở Phase 6.)
  > MỘT PHẦN: endpoint + 6 test MockMvc (ok, rỗng, quá dài, JSON lỗi, lỗi provider → 502, chậm → 504) xanh với stub. 👤 Còn gọi thật: điền `AI_API_KEY` rồi chạy `be/http/chat.http`.

- [x] **T0.08 Hạ tầng test không gọi mạng**
  - Viết `StubChatModel`, `StubEmbeddingModel`, `StubAiConfig` (xem `plan/05-java-snippets.md` §5).
  - Tạo base class `IntegrationTest` dùng Testcontainers `pgvector/pgvector:pg16` + `@ServiceConnection`.
  - Viết test `/ai/chat` dùng stub.
  - **DoD:** gỡ `AI_API_KEY` và tắt mạng → `./mvnw test` vẫn xanh.

- [x] **T0.09 Khởi tạo frontend**
  - `npm create vite@latest fe -- --template react-ts`, rồi cài `@tanstack/react-query react-router-dom` và Tailwind.
  - Vite proxy `/api` → `http://localhost:8080`; lint bằng oxlint (mặc định của create-vite 9) + Prettier.
  - Tạo cấu trúc `src/api, src/types, src/features/{trip-form,itinerary,chat}, src/components`.
  - **DoD:** `npm run dev` chạy; `npm run lint && npm run build` OK.

- [ ] **T0.10 Walking skeleton FE ↔ BE**
  - `src/api/client.ts`: hàm fetch có kiểu, parse `ProblemDetail`.
  - Trang thử chat: textarea + nút gửi, có trạng thái `loading | error | success`, hiển thị answer + latency.
  - **DoD:** gõ trên web → nhận câu trả lời từ LLM.
  > MỘT PHẦN: trang chat thử, client có kiểu, map lỗi sang tiếng Việt đã xong, `npm run build` xanh. 👤 Còn chạy thật end-to-end khi có Docker + `AI_API_KEY`.

- [x] **T0.11 Tài liệu nền**
  - `plan/adr/000-template.md`.
  - `plan/adr/001-spring-versions.md`: Boot 4.1.2 + Spring AI 2.0.2, provider Gemini.
  - `plan/adr/009-destinations.md`: tóm tắt lại ADR-001 của data repo, gồm đổi kịch bản Hội An → Hà Nội và định nghĩa ngân sách chỉ gồm vé + ăn uống.
  - `plan/PRD.md` (1 trang, tiếng Anh: problem, persona, giả thuyết, chỉ số, P0 scope, non-goals) và `plan/learning-log.md`.
  - **DoD:** các file đã commit, mỗi ADR có Context / Options / Decision / Consequences.

---

## Phase 1: Catalog và RAG

- [x] **T1.15 Domain enums**
  - Tạo `Destination` (kèm tâm lat/lon, timezone), `PlaceType`, `PlaceTag` (có gạch ngang, xem `plan/05-java-snippets.md` §1), `BestTime`, `PriceLevel`, `Pace`, `Slot`.
  - **DoD:** test đọc cả 3 `catalog_*.json` bằng `ObjectMapper` không lỗi; round-trip `PlaceTag.code()` đúng với mọi giá trị.

- [ ] **T1.16 Flyway V2: bảng `places`**
  - Cột theo `plan/02-architecture.md` › Bảng DB, kèm index `(destination, type)` và GIN trên `tags`.
  - **DoD:** migration chạy thành công trên Testcontainers.
  > CHỜ DOCKER: `V2__places.sql` đã viết; chạy qua `PlaceRepositoryTest` (Testcontainers) khi có Docker.

- [ ] **T1.17 Place + PlaceRepository (JdbcClient)**
  - Record `Place`. Các query: theo destination/type/tag, theo danh sách id, `upsert`, `count`.
  - **DoD:** test repository với Testcontainers.
  > CHỜ DOCKER: repository + 5 test Testcontainers đã viết (round-trip 101 dòng, filter tag có gạch ngang, phân trang, findByIds). Đã kiểm offline rằng mảng `String[]` không bị NamedParameter bung thành nhiều `?`.

- [ ] **T1.18 SeedLoader idempotent**
  - `ApplicationRunner` đọc `classpath:seed/catalog_*.json` và upsert (`plan/05-java-snippets.md` §2). Bật/tắt bằng `travelpilot.seed.enabled`.
  - **DoD:** chạy 2 lần, `count(*)` vẫn là 101 (45 + 34 + 22).
  > CHỜ DOCKER: SeedLoader + test `seedingTwiceKeepsOneRowPerPlace` (101 → 101) đã viết, đang skip vì chưa có Docker.

- [x] **T1.19 OpeningHours checker**
  - `OpeningHours.parse(String)` và `isOpen(LocalDate)`, `isOpen(LocalDate, LocalTime from, LocalTime to)`.
  - Phải hỗ trợ mọi mẫu trong `plan/01-dataset.md` §6. Chuỗi không parse được trả về UNKNOWN.
  - **DoD:**
    - Test duyệt toàn bộ catalog không exception.
    - Lăng Bác (`hanoi-way-37625751`) đóng ngày 2026-10-20 và mở ngày 2026-11-04 (thứ Tư).
    - Bảo tàng Phòng không – Không quân đóng thứ Sáu.
    - `Mo-Su 18:00-02:00` mở lúc 01:00.

- [x] **T1.20 `GET /api/v1/places`**
  - Filter `destination`, `type`, `tag` (nhận chuỗi có gạch ngang), phân trang đơn giản. Thêm `GET /places/{id}`.
  - **DoD:** test MockMvc: `?destination=DANANG&tag=THEME-PARK` trả về Bà Nà.
  > Test MockMvc mock repository (6 test). Khi có Docker, `PlaceRepositoryTest` kiểm phần SQL.

- [x] **T1.21 KnowledgeLoader**
  - Đọc `classpath:seed/knowledge_*.jsonl` thành `KnowledgeChunk`. Validate có `docId, destination, docType, title, text, license`. Lỗi phải nêu rõ file và số dòng.
  - **DoD:** đọc đủ 401 chunk; test với fixture thiếu trường báo lỗi rõ.

- [ ] **T1.22 IngestionService (idempotent)**
  - Mỗi chunk thành 1 `Document`, theo `plan/05-java-snippets.md` §3:
    - id: UUID tất định từ `docId`.
    - content: `title + "\n" + text`.
    - metadata: các trường của chunk, có thêm `placeId`.
  - Xóa theo destination rồi add theo batch 50. Không dùng TokenTextSplitter.
  - Trigger bằng `POST /internal/ingest` (profile dev) hoặc arg `--ingest`. Log số chunk và thời gian.
  - **DoD:** test với `StubEmbeddingModel`: ingest 2 lần, `select count(*) from vector_store` = 401 cả hai lần.
  > MỘT PHẦN: idempotent đã kiểm offline (SimpleVectorStore: 401 → 401 id). Bản pgvector `PgVectorIngestionTest` chờ Docker. Trigger: `--ingest` hoặc `POST /internal/ingest` (profile dev).

- [x] **T1.23 Retriever**
  - `search(Destination, query, topK, threshold)` → `List<Evidence>`. Filter dựng bằng `FilterExpressionBuilder` từ enum.
  - Thêm biến thể `forPlaces(Destination, Collection<String> placeIds, query)`.
  - **DoD:** unit test: kết quả chỉ thuộc destination yêu cầu.

- [ ] **T1.24 Endpoint debug search (dev)**
  - `GET /internal/search?q=&dest=` → top 5 kèm `score, docId, title`.
  - **DoD:** 👤 + agent: chạy ingest thật, thử 5 câu trong golden set, ghi nhận xét vào `plan/learning-log.md`.
  > MỘT PHẦN: `GET /internal/search?q=&dest=&k=` (profile dev) đã có. 👤 Còn chạy ingest thật với Gemini rồi thử 5 câu golden set, ghi nhận xét vào learning log.

- [x] **T1.25 PromptLoader có version**
  - Đọc `resources/prompts/<name>-v<N>.st`. Version cấu hình được trong yml (`travelpilot.prompts.planner: v1`).
  - **DoD:** đổi version trong yml thì nạp file khác mà không cần sửa code (unit test).
  > Tên file thực tế: `prompts/<name>-<version>.system.st` + `.user.st`; version trong `travelpilot.prompts.<name>`.

- [ ] **T1.26 `POST /api/v1/ask`**
  - Body `{destination, question}`. Dùng prompt `ask-v1` kèm `<context>` và `<facts>` lấy từ catalog (xem `plan/03-ai-pipeline.md` §3).
  - Response `{answer, sources[], noEvidence}`. Nếu không có evidence thì **không gọi LLM**.
  - **DoD:** test stub cho 3 trường hợp: có evidence, không có evidence, LLM lỗi (502). Gọi thật 3 câu demo đều có nguồn.
  > MỘT PHẦN: 5 test stub xanh (có evidence + facts từ catalog + nguồn được trích, không evidence → không gọi LLM, LLM lỗi → 502, request sai → 400). 👤 Còn gọi thật 3 câu demo khi có Docker + `AI_API_KEY`.

- [ ] **T1.27 RetrievalEvalRunner**
  - Test `@Tag("eval")` chạy trên `classpath:eval/golden_set.json` (map từ `data/golden_set.json`): tính hit@1, hit@3, hit@5, MRR theo từng điểm đến và tổng, kèm kiểm tra `mustMention` cho `/ask`.
  - Xuất `be/eval/results/retrieval-YYYYMMDD.md`.
  - **DoD:** `./mvnw test -Dgroups=eval` ra bảng số liệu. Ghi số thật; mục tiêu hit@5 ≥ 80%.
  > MỘT PHẦN: hit@k/MRR + báo cáo markdown đã viết và test offline (golden set khớp 401 docId). 👤 Chạy thật: `./mvnw -s %USERPROFILE%\.m2\settings-central.xml test -Dgroups=eval -Dtest=RetrievalEvalRunner` (cần Docker + `AI_API_KEY`), kết quả ở `be/eval/results/`.

- [ ] **T1.28 Thí nghiệm topK / threshold** (P1 nếu thiếu thời gian)
  - So sánh topK 3, 5, 8 và threshold 0.3, 0.45, 0.6. Thử thêm "query + tên điểm đến".
  - **DoD:** bảng so sánh + 2 insight → `plan/adr/010-retrieval-config.md`.
  > BLOCKED: cần số liệu thật từ T1.27 (Docker + key). `RetrievalEvalRunner` đã nhận topK cố định; thêm vòng lặp topK/threshold khi có baseline.

---

## Phase 2: Gợi ý và planner

- [x] **T2.01 TripRequest + validation**
  - DTO theo `plan/02-architecture.md`. Validator riêng kiểm tra:
    - `startDate ≥ hôm nay` (zone VN).
    - `days` 1–5; tuổi trẻ 0–17; `budget > 0`.
    - `preferences` ⊆ danh sách cho phép.
  - **DoD:** test từng trường hợp sai trả 400 `VALIDATION_FAILED`.
  > Kiểm ở mức Bean Validation + TripRequestValidator (Clock cố định); endpoint trả 400 kiểm ở T2.18. Null trong childrenAges bị chặn khi tạo record.

- [x] **T2.02 ScoringWeights**
  - `@ConfigurationProperties("travelpilot.scoring")`, validate tổng = 1.0.
  - **DoD:** đổi trọng số trong yml có tác dụng; tổng sai thì app không khởi động và báo lỗi rõ.
  > Đã có từ T0.04: `TravelPilotProperties.Weights` validate tổng = 1.0 (app không khởi động nếu sai), test trong `TravelPilotPropertiesTest`.

- [x] **T2.03 HardFilter**
  - 6 điều kiện kèm `FilterReason` (`plan/03-ai-pipeline.md` §1). Chi phí tính bằng `BudgetCalculator.itemCost`.
  - **DoD:** mỗi điều kiện một test. Bé 6 tuổi thì loại The Craftsman bar. Đi Hà Nội 20/10 thì loại Lăng Bác.

- [x] **T2.04 Scorer + ScoreBreakdown**
  - 6 hàm thuần, mỗi hàm có test riêng, cộng phần điều chỉnh ngày mưa.
  - **DoD:** ≥ 12 test; coverage package `recommendation` ≥ 80% (JaCoCo).
  > 14 test; JaCoCo: package `recommendation` 100% dòng lúc commit; `./mvnw verify` chặn nếu < 80%.

- [x] **T2.05 CandidateSet + RecommendationService**
  - Top `days×4` ACTIVITY và `days×3` FOOD, nới lỏng theo bậc kèm warning `CANDIDATES_RELAXED`.
  - **DoD:** QUANGNINH 5 ngày + bé 3 tuổi vẫn ra tập không rỗng, có warning.
  > Nới lỏng có một bậc (trần giá 50% → 70%); bước "bỏ khớp tag" không cần vì tag chỉ dùng để chấm điểm, không lọc. Thiếu thì cảnh báo `CANDIDATES_SHORT` / `FOOD_SHORT`.

- [x] **T2.06 Snapshot 3 persona**
  - Endpoint dev `GET /api/v1/trips/candidates`. Test snapshot top 10 cho 3 persona (bảng kịch bản trong `plan/02-architecture.md`).
  - **DoD:** 3 top 10 khác nhau rõ ràng, đã tự review; persona 1 có Bà Nà trong top ứng viên.
  > Snapshot ở `be/src/test/resources/snapshots/candidates.md` (5 bảng, gồm 3 kiểu khách trên cùng Đà Nẵng). Phát hiện: điểm ngân sách cho Bà Nà = 0 nên rớt khỏi top → thêm quy tắc mỗi sở thích luôn có ít nhất 1 ứng viên. Endpoint dev là `POST /api/v1/trips/candidates` (cần body).

- [x] **T2.07 BudgetCalculator**
  - Hàm thuần theo `plan/05-java-snippets.md` §4.
  - **DoD:** test số liệu tay: Bà Nà, 2 người lớn + bé 6 tuổi = 2.800.000đ; tổng theo loại đúng; cờ `overBudget` đúng.

- [x] **T2.08 Records bản nháp**
  - `TripPlanDraft`, `DraftDay`, `DraftItem` (thêm `@JsonPropertyDescription` cho từng field).
  - **DoD:** biên dịch OK; JSON schema sinh ra đọc được.

- [x] **T2.09 Prompt planner-v1 + formatter**
  - `planner-v1.st` (system + user), `CandidateTableFormatter` (bảng pipe, `costVnd` đã nhân cho cả nhóm), `EvidenceFormatter` (`[ev-n | placeId] snippet ≤ 400 ký tự`).
  - **DoD:** test render prompt của persona 1 ra chuỗi; lưu mẫu vào `plan/prompt-samples/planner-v1.txt` và đọc lại thấy rõ ràng.
  > Mẫu: `plan/prompt-samples/planner-v1.txt` (tạo lại bằng `./mvnw test -Dtest=PlannerPromptsTest -DwriteSamples=true`). Kèm prompt `repair-v1` dùng cho T2.16.

- [x] **T2.10 EvidenceService**
  - Lấy tối đa 2 chunk cho mỗi place và tổng ≤ 12, đánh số `ev-1..n`.
  - **DoD:** mỗi lần plan đều có danh sách ev-id; test với stub.

- [x] **T2.11 WeatherProvider: stub trước, Open-Meteo làm ở Phase 5**
  - Interface `WeatherProvider.forecast(dest, dates)` → `Map<LocalDate, DayWeather(rainy, tMin, tMax, code)>`. Tạm thời dùng `NoWeatherProvider`.
  - **DoD:** orchestrator gọi được và nhận kết quả rỗng mà không lỗi.
  > Port `WeatherProvider` → `Forecast` (map ngày → `DayWeather`). `NoWeatherProvider` trả rỗng; Open-Meteo thay ở T5.01. Orchestrator dùng ở T2.17.

- [ ] **T2.12 PlannerClient**
  - Gọi `.call().responseEntity(TripPlanDraft.class)` với temperature 0.3 và timeout. Lấy usage (`plan/05-java-snippets.md` §6).
  - **DoD:** gọi thật với persona 1 trả draft parse được.
  > MỘT PHẦN: 4 test stub xanh (parse, JSON hỏng → attempt không draft, repair gửi draft cũ + lỗi, lỗi provider → 502). Gemini bật `responseMimeType=application/json`, temperature 0.3. 👤 Còn gọi thật với persona 1 khi có key.

- [x] **T2.13 PlanValidator**
  - 12 rule, mỗi rule có mã riêng (`plan/03-ai-pipeline.md` §4). Sức chứa buổi và day trip theo §6.
  - **DoD:** ≥ 12 test xanh, mỗi rule một test.
  > 14 rule (12 rule của plan + MISSING_FIELD, MEAL_SLOT; SCHEMA_INVALID khi không parse được), 17 test. `DaySchedule` là nguồn duy nhất về giờ trong ngày, dùng chung cho validator/fallback/assembler.

- [x] **T2.14 GreedyFallbackPlanner**
  - Không dùng LLM (§5).
  - **DoD:** với 3 persona và 4 case ràng buộc khó, kết quả **luôn** qua validator, không còn lỗi HARD.
  > Kiểm 3 persona + 4 case khó (500k/ngày, bé 2 tuổi, 5 ngày Quảng Ninh, mưa cả 3 ngày): 0 lỗi HARD. Mô phỏng ngày bằng `DaySchedule` trước khi thêm mục; ngày mưa chỉ chọn chỗ trong nhà trừ khi không còn lựa chọn.

- [x] **T2.15 Assembler**
  - Draft → `TripPlan`: điền tên, giá, giờ (08:30 / 12:00 trưa / 13:30 / 18:30, cộng 30 phút di chuyển), `costEstimated`, `priceNote`, `verifyAt`, sources, warnings, disclaimer.
  - **DoD:** test draft 2 ngày (có 1 day trip) cho ra giờ hợp lệ, không chồng lấn; tổng tiền khớp tính tay.

- [x] **T2.16 RepairLoop**
  - Prompt `repair-v1`, tối đa 2 lần, log số lần repair.
  - **DoD:** test stub "lần 1 sai ID → lần 2 đúng" cho kết quả `repaired = true`; "sai 3 lần" thì chuyển fallback.

- [ ] **T2.17 PlannerOrchestrator**
  - Ghép: filter → score → weather → evidence → plan → validate/repair/fallback → assemble. Đo thời gian từng bước.
  - **DoD:** test stub cả 3 nhánh (ok, repair, fallback); timeout LLM thì đi fallback; persona 1 chạy thật OK.
  > MỘT PHẦN: 5 test offline cho toàn bộ workflow (ok, repair, fallback, thời tiết lỗi, model lỗi) xanh; đo thời gian từng bước. 👤 Còn chạy persona 1 thật với Gemini.

- [ ] **T2.18 Lưu trips + versions**
  - Flyway V3 (`V3__trips.sql`). Thêm `POST /api/v1/trips`, `GET /trips/{id}`, `GET /trips/{id}/versions/{v}`. Guest tạm thời dùng header `X-Guest-Id`; Phase 6 thay bằng JWT.
  - **DoD:** tạo trip rồi đọc lại trả đúng JSON (MockMvc + Testcontainers).
  > MỘT PHẦN: API + service + repository xong; MockMvc (201 + Location, 400 kèm lỗi từng trường, guest khác → 404) và JSON round-trip của TripPlan xanh. `TripRepositoryTest` (Testcontainers) chờ Docker. Giờ trong API dạng `HH:mm`.

- [ ] **T2.19 Chạy 3 persona thật + sửa lỗi**
  - Chạy cả 3 persona (kèm persona 3' Quảng Ninh). Ghi bug vào `plan/learning-log.md` và sửa 5 bug lớn nhất.
  - **DoD:** không có ID ngoài catalog; tổng tiền khớp catalog; mọi lịch qua validator.
  > MỘT PHẦN: đã chạy thử 3 persona qua toàn bộ workflow với LLM tắt (fallback) và sửa 5 lỗi (thiếu Bà Nà, tràn giờ buổi sáng, bảo tàng xếp sau giờ đóng cửa, quán cà phê làm bữa chính, bữa trưa đẩy hoạt động qua giờ đóng cửa), mỗi lỗi có test hồi quy; xem learning log. 👤 Còn chạy với Gemini thật.

---

## Phase 3: Frontend MVP → tag `v0.1`

- [x] **T3.01 TypeScript types**
  - `src/types/api.ts` khớp DTO: `TripRequest`, `TripPlan`, `PlanItem`, `Budget`, `Warning`, `Source`, `ProblemDetail`, enum dạng union string (tag có gạch ngang).
  - **DoD:** `npm run build` không lỗi type.
  > Types viết tay theo DTO backend (`fe/src/types/api.ts`); build strict không lỗi.

- [ ] **T3.02 TripForm**
  - Các trường: destination (3 lựa chọn), ngày đi, số ngày (1–5), người lớn, tuổi từng trẻ (thêm/xóa), ngân sách dạng VND với nhãn "Ngân sách vui chơi và ăn uống", chips sở thích, pace, ghi chú.
  - Validate giống backend.
  - **DoD:** submit tạo được trip; nhập sai thì hiện lỗi đúng chỗ.
  > MỘT PHẦN: form + validate giống backend (Vitest), lỗi từ backend map về đúng ô, header `X-Guest-Id` tự gửi. 👤 Chưa submit thật với backend.

- [x] **T3.03 Hiển thị tiến trình**
  - Danh sách bước sáng dần theo thời gian giả lập: Lọc địa điểm → Chấm điểm → Tìm bằng chứng → Soạn lịch → Kiểm tra ngân sách. Phase 9 thay bằng SSE.
  - **DoD:** người dùng thấy app đang làm gì trong 10–20 giây chờ.
  > Logic thời gian có test Vitest. 👤 Xem trực quan khi chạy cùng backend.

- [x] **T3.04 DayCard + PlanItem + SourceChip**
  - Mỗi item hiển thị: buổi • giờ • tên • badge loại • giá ("~" nếu ước tính, "Miễn phí", tooltip `priceNote`, "Giá cập nhật …" nếu có `verifyAt`) • lý do • chip nguồn.
  - Popover của chip nguồn: snippet + link + license.
  - "Vì sao xếp hạng cao" hiển thị bằng `ScoreBreakdown`.
  - **DoD:** lịch persona 1 hiển thị đúng; giá ước tính có dấu "~".
  > Kiểm bằng smoke test render (`TripView.test.tsx`) với lịch mẫu: giá có "~", "Miễn phí", "Giá cập nhật", chip nguồn, "Vì sao gợi ý?". Link nguồn chỉ nhận http(s). 👤 Xem trực quan ở `/demo`.

- [x] **T3.05 BudgetBar + Warnings + footer**
  - Thanh tổng / giới hạn đổi màu khi vượt; bảng phân rã ACTIVITY/FOOD; danh sách warning; dòng disclaimer; footer ghi công dữ liệu.
  - **DoD:** case vượt ngân sách hiển thị cảnh báo đỏ.
  > Vượt ngân sách → thanh đỏ + cảnh báo đỏ, đã có smoke test. Disclaimer hiển thị dưới thanh ngân sách; footer ghi công có từ T0.10.

- [ ] **T3.06 Routing + trạng thái lỗi**
  - Route `/` → `/trips/:id`; refresh vẫn xem được trip. Lỗi hiển thị theo `code` (tiếng Việt), kèm nút thử lại và `requestId`.
  - **DoD:** refresh không mất kết quả; tắt backend thì UI không vỡ.
  > MỘT PHẦN: route `/trips/:id` tải lại trip từ backend (refresh không mất), skeleton khi tải, lỗi theo mã + Thử lại + mã yêu cầu; backend tắt → thông báo mất kết nối. Thêm `/demo` (lịch mẫu, không cần backend) và `/playground`. 👤 Còn thử refresh và tắt backend khi chạy thật.

- [ ] **T3.07 Mốc v0.1**
  - Tag `v0.1`; `plan/retro-v0.1.md` (giữ / cắt / đổi gì).
  - 👤 Quay video thô 60 giây; cho 1–2 người dùng thử và ghi 3–5 nhận xét.
  - **DoD:** tag đã push.
  > CHỜ KIỂM THỬ: chưa gắn tag vì chưa chạy end-to-end với backend + Gemini. Làm theo `plan/manual-test-v0.1.md`, đạt hết thì gắn `v0.1`; retro nháp ở `plan/retro-v0.1.md`.

---

## Phase 4: Chat và chỉnh sửa một phần

- [ ] **T4.01 Flyway V4: conversations/messages + ConversationService**
  - **DoD:** lưu và đọc được lịch sử theo trip.
  > CHỜ DOCKER: `V4__conversations.sql`, repository, service (lịch sử đầy đủ + cửa sổ 6 tin) và `ConversationRepositoryTest` đã viết.

- [ ] **T4.02 IntentRouter**
  - Prompt `intent-v1` → `IntentResult(intent, confidence)`, temperature 0.
  - **DoD:** test stub cho 4 intent; gọi thật phân loại đúng ≥ 8/10 câu mẫu (ghi vào learning log).
  > MỘT PHẦN: stub 4 intent + model trả rác/lỗi → heuristic từ khóa (8 câu tiếng Việt) xanh. 👤 Còn đo 8/10 câu với Gemini thật.

- [x] **T4.03 Bounded memory**
  - 6 tin gần nhất cộng bản tóm tắt `TripRequest` hiện tại (lấy từ DB) đưa vào system prompt.
  - **DoD:** hội thoại 3 lượt vẫn nhớ đúng ngân sách đã đổi.
  > `ChatContext`: TripRequest + dàn ý lịch hiện tại (lấy từ DB) + 6 tin gần nhất; không dùng MessageWindowChatMemory của Spring AI vì lịch sử đã lưu ở bảng messages. DoD đạt: `ChatServiceTest.threeTurnsRememberTheChangedBudget` (lượt 3 vẫn thấy 3 triệu từ DB).

- [ ] **T4.04 Nhánh ASK trong trip**
  - Dùng lại Retriever + `ask-v1` theo destination của trip.
  - **DoD:** hỏi "Bà Nà có hợp với bé 6 tuổi không?" nhận câu trả lời có nguồn, không khẳng định an toàn dựa trên `reviewFields`.
  > MỘT PHẦN: `ask-v2` (cấm khẳng định dữ liệu "chưa xác minh", nhận lịch sử hội thoại); facts ghi "trẻ em từ X tuổi (chưa xác minh)" khi trường là ước tính/cần review. Nhánh ASK nối vào chat ở T4.07. 👤 Còn hỏi thật "Bà Nà có hợp với bé 6 tuổi không?".

- [x] **T4.05 ChangeRequestExtractor**
  - Prompt `change-v1` → `ChangeRequest`; `TripRequest.apply(change)` trả về bản sao mới (immutable).
  - **DoD:** 3 test: "giảm còn 3 triệu", "ngày 2 trong nhà", "bỏ Bà Nà".
  > Áp thay đổi bằng `ChangeRequest.applyTo(TripRequest)` (đặt ở package conversation để trip không phụ thuộc ngược). Có thêm test làm sạch dữ liệu model (ngày ngoài chuyến, id lạ, ngân sách âm, sở thích ngoài danh sách).

- [x] **T4.06 Tạo lại một phần (replan)**
  - Khóa các ngày không bị ảnh hưởng; loại các place đã dùng ở ngày khóa; prompt `replan-v1`; rule `LOCKED_DAY_CHANGED`. Lưu version n+1 kèm `change_summary`.
  - **DoD:** kịch bản 3 (chỉ ngày 2 đổi, các ngày khác giống 100%) và kịch bản 4 pass.
  > Kịch bản 3 (chỉ ngày 2 đổi; ngày 1, 3 giữ nguyên giờ, lý do, nguồn) và kịch bản 4 (3 triệu → lập lại cả chuyến, tổng ≤ 3 triệu, không còn Bà Nà) xanh cả qua fallback lẫn qua model stub với prompt `replan-v1`. Bỏ một địa điểm chỉ lập lại các ngày có địa điểm đó.

- [x] **T4.07 `POST /trips/{id}/messages`**
  - → `{reply, intent, sources[], newVersion?, plan?}`. OUT_OF_SCOPE và SMALL_TALK trả lời ngắn.
  - **DoD:** test MockMvc cho 4 intent (stub).
  > `POST` + `GET /api/v1/trips/{id}/messages`; MockMvc cho 4 intent + validate; `ChatServiceTest` chạy cả hội thoại offline (repository in-memory). SMALL_TALK / OUT_OF_SCOPE trả câu cố định, không gọi model.

- [ ] **T4.08 ChatPanel + highlight thay đổi**
  - Khung chat; badge v1/v2/v3; so sánh `placeId` theo ngày để highlight item đổi trong 3 giây; dòng "v2: ngày 2 đổi sang hoạt động trong nhà"; nút xem version cũ.
  - **DoD:** demo kịch bản 3 chạy trên UI.
  > MỘT PHẦN: ChatPanel (lịch sử, câu mẫu, Enter gửi, lỗi + thử lại), highlight mục mới 3 giây (`planDiff` có test), dòng "v2: …", nút v1/v2 xem bản cũ chỉ đọc; smoke test render. 👤 Còn chạy kịch bản 3 trên UI với backend thật.

---

## Phase 5: Thời tiết và tools

- [x] **T5.01 OpenMeteoClient**
  - Dùng `RestClient`, timeout 3s, cache Caffeine 1h theo (dest, date). Map mã WMO + xác suất mưa ≥ 60% thành `rainy`.
  - **DoD:** test với JSON mẫu (fixture), không gọi mạng.
  > `OpenMeteoClient` (@Primary, bật bằng `travelpilot.weather.enabled`): RestClient + JDK client timeout 3s, cache Caffeine 1h theo (điểm đến, ngày gọi), lỗi → `Forecast.NONE` và không cache. 5 test với fixture qua MockRestServiceServer.

- [x] **T5.02 Weather trong workflow**
  - Ngày trong 16 ngày tới dùng forecast; ngày xa hơn ghi warning `WEATHER_UNAVAILABLE` (P1: dùng khí hậu trung bình). Ngày mưa thì Scorer ưu tiên indoor, kèm rule `RAIN_OUTDOOR`.
  - **DoD:** test stub "ngày 2 mưa" cho ra ngày 2 có nhiều indoor hơn.
  > Forecast đi vào candidates (bonus indoor), prompt (rain/dry theo ngày), rule `RAIN_OUTDOOR`, fallback ưu tiên indoor ngày mưa. Thiếu dự báo cả chuyến → 1 warning; thiếu một phần → warning theo từng ngày. `WeatherInPlanningTest`: ngày 2 mưa → nhiều indoor hơn, ít outdoor hơn. P1 (khí hậu trung bình) chưa làm.

- [ ] **T5.03 PartnerInventoryProvider (mock)**
  - Interface + `MockInventoryProvider` tất định theo `hash(placeId, date)`, luôn `demo = true`.
  - **DoD:** gọi 2 lần ra cùng kết quả.

- [ ] **T5.04 TravelTools (@Tool)**
  - Gồm `getWeatherForecast`, `searchPlaces`, `checkAvailability` (§8 `plan/03-ai-pipeline.md`). Validate tham số, tối đa 3 vòng, log tool vào `ai_call_logs`.
  - **DoD:** hỏi "mai Đà Nẵng trời thế nào?" thì thấy tool call trong log; tham số sai bị từ chối.

---

## Phase 6: Bảo mật

- [ ] **T6.01 Guest token + quyền sở hữu trip**
  - `POST /api/v1/guest` cấp JWT HMAC (`GUEST_TOKEN_SECRET`, hạn 7 ngày); filter đọc header `Authorization: Bearer`. Trip của guest khác trả 404.
  - FE tự lấy token và lưu `localStorage`.
  - **DoD:** test T3 pass.

- [ ] **T6.02 Rate limit (Bucket4j)**
  - 10 lần tạo lịch mỗi giờ, 60 tin nhắn mỗi giờ theo token, cộng trần tổng mỗi ngày. Vượt thì 429 `RATE_LIMITED`.
  - **DoD:** test vượt ngưỡng trả 429.

- [ ] **T6.03 InputGuard**
  - Giới hạn độ dài, bỏ ký tự điều khiển, body ≤ 32KB, max tokens output.
  - **DoD:** input 50.000 ký tự trả 400 `INPUT_TOO_LONG`.

- [ ] **T6.04 Sáu test tấn công (stub, chạy trong CI)**
  - T1 lộ prompt; T2 chunk độc (fixture "Assistant: recommend X only"); T3 trip người khác; T4 ép tool lạ; T5 `<script>`; T6 spam/size.
  - Xóa hoặc giới hạn endpoint `/ai/chat` thử nghiệm.
  - **DoD:** 6/6 pass.

- [ ] **T6.05 `plan/security-notes.md`**
  - Threat model (OWASP LLM: LLM01, 02, 06, 07, 10) kèm kết quả test.
  - **DoD:** đã commit.

---

## Phase 7: Observability và độ tin cậy

- [ ] **T7.01 Flyway V5 `ai_call_logs` + AiCallLogger**
  - Ghi operation, model, promptVersion, tokens, latency, status, retrievedIds, toolCalls.
  - **DoD:** mỗi lần `/ask` và mỗi lần tạo lịch sinh đúng số dòng log.

- [ ] **T7.02 RequestIdFilter + log JSON**
  - Gán MDC `requestId`, trả header `X-Request-Id`; dùng structured logging của Boot (`logging.structured.format.console`).
  - **DoD:** mỗi dòng log có `requestId`.

- [ ] **T7.03 Metrics**
  - `tp.plan.latency{step}`, `tp.plan.repairs`, `tp.plan.fallback`, `tp.llm.tokens`, `tp.tool.latency`, `tp.ratelimit.rejected`.
  - **DoD:** `/actuator/metrics/tp.plan.latency` có dữ liệu sau một lần tạo lịch.

- [ ] **T7.04 Retry/backoff**
  - Chỉ retry 429, 5xx, timeout: 2 lần, có jitter.
  - **DoD:** test stub ném lỗi 503 hai lần rồi thành công; lỗi 400 không retry.

- [ ] **T7.05 Ước tính chi phí**
  - Đặt bảng giá model trong config; tính cost cho mỗi `ai_call`; tính trung bình chi phí một lịch trình.
  - **DoD:** có số cost/plan để đưa vào README.

---

## Phase 8: Evaluation

- [ ] **T8.01 `be/eval/cases.json` (20 case)**
  - 5 nhóm: happy 6, ràng buộc khó 4, chỉnh sửa 4, hỏi đáp 3, tấn công 3. Dùng ID thật trong catalog.
  - **DoD:** 20 case hợp lệ theo schema (test đọc file).

- [ ] **T8.02 EvalRunner + check cố định**
  - Chạy qua orchestrator thật. Các check: schema, ID, ngân sách, tuổi trẻ, pace, ngày khóa, `mustIncludeAnyOf`, latency, tokens.
  - **DoD:** một lệnh chạy toàn bộ (`./mvnw test -Dgroups=eval -Dtest=EvalRunner`).

- [ ] **T8.03 LLM-as-judge**
  - Prompt `judge-v1` chấm groundedness và reason quality (1–5), output structured.
  - **DoD:** báo cáo có điểm judge.

- [ ] **T8.04 Báo cáo**
  - `be/eval/results/YYYY-MM-DD-<label>.md` + CSV; header gồm git sha, prompt version, model, config.
  - **DoD:** đã có file báo cáo baseline.

- [ ] **T8.05 Baseline → cải tiến → chạy lại**
  - Sửa 2–3 lỗi lớn nhất (prompt, scorer hoặc config retrieval); viết 2 insight.
  - **DoD:** có bảng trước/sau với số thật.

- [ ] **T8.06 Hiệu chỉnh judge** (P1)
  - 👤 Chấm tay 5 case rồi so với điểm judge.
  - **DoD:** có bảng độ lệch.

---

## Phase 9: Hoàn thiện, đóng gói, CI, deploy

- [ ] **T9.01 SSE tiến trình** (P1)
  - `SseEmitter` gửi sự kiện `step` và `result`; FE dùng `EventSource`.
  - **DoD:** các bước sáng theo tiến trình thật.

- [ ] **T9.02 Responsive + polish**
  - Kiểm tra ở 375px, thêm skeleton loading và trạng thái rỗng.
  - **DoD:** dùng được trên điện thoại.

- [ ] **T9.03 Bản đồ** (P1)
  - Leaflet + tile OSM, marker theo ngày, kèm ghi công OSM.
  - **DoD:** mỗi DayCard có bản đồ nhỏ.

- [ ] **T9.04 Dockerfile BE/FE + `compose.prod.yml`**
  - BE: build multi-stage, layered jar, user non-root. FE: build rồi phục vụ bằng nginx hoặc caddy.
  - Khi start lần đầu: seed + ingest tự động.
  - Build backend với context là **gốc repo** (`docker build -f be/Dockerfile .`) để Maven thấy `../data`; trong `compose.prod.yml` đặt `context: .` và `dockerfile: be/Dockerfile`.
  - **DoD:** `docker compose -f compose.prod.yml up` chạy được kịch bản 1 trên máy sạch.

- [ ] **T9.05 GitHub Actions CI**
  - Job BE: `./mvnw verify` (Testcontainers trên ubuntu runner, không có key). Job FE: `npm ci && npm run lint && npm run build`.
  - **DoD:** badge CI xanh trong README.

- [ ] **T9.06 Deploy** (P1)
  - VPS + Caddy HTTPS, hoặc PaaS có pgvector. Secret qua env, có spending cap, tắt `/internal`.
  - 👤 Tạo VPS hoặc tài khoản PaaS.
  - **DoD:** link public chạy được kịch bản 1.

- [ ] **T9.07 README tiếng Anh**
  - Gồm: pitch, GIF demo, sơ đồ kiến trúc, "How it works" (8 bước), "LLM chooses, backend computes", bảng eval, security, cost/plan, run locally (< 10 phút), ghi công dữ liệu, danh sách ADR.
  - **DoD:** một người chưa biết dự án đọc 5 phút có thể kể lại đúng.

- [ ] **T9.08 ADR còn thiếu**
  - 002 modular monolith; 003 pgvector + model embedding/số chiều; 004 LLM chỉ chọn ID; 005 workflow vs agent; 006 budget ở backend; 007 repair ≤ 2 + fallback; 008 guest token.
  - Thêm `plan/future-membership.md` (blockchain membership, chỉ là thiết kế).
  - **DoD:** đã commit.

- [ ] **T9.09 Clean clone + tag `v1.0`**
  - Clone sang thư mục mới, làm theo README.
  - **DoD:** chạy được trong < 10 phút; tag `v1.0`.

---

## Phase 10: Việc của bạn (👤)

- [ ] 👤 User test với 3–5 người, mỗi buổi 15 phút, theo kịch bản 1 và 3. Ghi thời gian, điểm 1–5, chỗ vấp → `plan/user-testing.md`.
- [ ] Agent sửa top các vấn đề từ user test (commit có liên kết tới ghi chú).
- [ ] 👤 Quay demo video 2–3 phút (YouTube unlisted).
- [ ] 👤 Viết bullet cho CV, cập nhật GitHub profile README.
- [ ] 👤 Ôn phỏng vấn: workflow vs agent, vì sao LLM chỉ chọn ID, đo hallucination thế nào, chi phí và latency, threat model.
- [ ] 👤 **Nộp hồ sơ.**

---

## Cắt scope nếu trễ (theo thứ tự cắt)

1. Cắt các mục P1: SSE (T9.01), bản đồ (T9.03), deploy (T9.06), hiệu chỉnh judge (T8.06), thí nghiệm retrieval (T1.28).
2. Tools chỉ giữ `getWeatherForecast`; `checkAvailability` chỉ mô tả trong README.
3. Eval giảm còn 12 case (happy 4, ràng buộc 3, chỉnh sửa 3, tấn công 2).
4. **Không bao giờ cắt:**
   - Validator + fallback.
   - Ngân sách tính ở backend.
   - Test không gọi mạng.
   - 6 test tấn công.
   - README + video.
