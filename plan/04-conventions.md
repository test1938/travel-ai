# Quy ước code, test, bảo mật

> **Đây là luật bắt buộc**, không phải gợi ý. Agent và người code phải theo các mục dưới đây cho mọi thay đổi. Nếu một luật cản trở công việc, ghi đề xuất vào `plan/adr/` và hỏi trước, không tự bỏ qua.

## Java / Spring

- Java 21, Spring Boot 4.1, Spring AI 2.0, Jackson 3 (`tools.jackson.*`; annotation vẫn ở `com.fasterxml.jackson.annotation`). DTO dùng `record`. Gọi I/O chậm (LLM, Open-Meteo) thì cân nhắc bật virtual threads bằng `spring.threads.virtual.enabled=true`.
- Inject qua constructor, không dùng `@Autowired` trên field. Cấu hình đặt trong `@ConfigurationProperties` có `@Validated`, prefix `travelpilot.*`.
- Logic nghiệp vụ (HardFilter, Scorer, Validator, Assembler, BudgetCalculator, OpeningHours) viết thành **hàm thuần**, không phụ thuộc Spring, để unit test nhanh.
- Tiền tệ dùng `long` VND, không dùng `double`. Thời gian dùng `LocalDate`, `LocalTime`, zone `Asia/Ho_Chi_Minh`.
- Enum lưu xuống DB bằng tên chuỗi, riêng tag thì lưu chuỗi JSON có gạch ngang.
- Prompt không hard-code trong Java. Đặt ở `resources/prompts/*.st`, tên file chứa version, và version được log lại.
- Code dùng tiếng Anh: tên biến, class, comment. Chuỗi hiển thị cho người dùng dùng tiếng Việt.

## Cấu trúc và pattern (backend)

**Package theo tính năng, không theo tầng.**
- Mỗi tính năng là một package: `catalog/`, `knowledge/`, `recommendation/`, `planner/`, `trip/`… Controller, service, repository, DTO của tính năng nằm chung package đó.
- Không tạo các package chung kiểu `controllers/` hay `services/`.
- `common/` chỉ chứa thứ dùng chung thật sự: lỗi, config, enum domain.

**Ba lớp trong mỗi tính năng:**

| Lớp | Được làm | Không được làm |
|---|---|---|
| Controller (`@RestController`) | Nhận và validate DTO, gọi **một** method service, trả DTO | Chứa logic nghiệp vụ, gọi repository hay `ChatClient` trực tiếp |
| Service (`@Service`) | Điều phối: gọi repository, gọi AI, gọi các hàm thuần | Viết SQL, tự build prompt string (dùng `PromptLoader`) |
| Repository (`JdbcClient`) | SQL và map dòng ↔ record | Logic nghiệp vụ |

**Pattern dùng ở đâu** (chỉ dùng khi đúng chỗ, không thêm abstraction "cho đẹp"):

| Chỗ | Pattern | Lý do |
|---|---|---|
| `PlanValidator` | **Strategy / Chain**: mỗi rule một class implement `PlanRule`; validator nhận `List<PlanRule>` qua DI | Thêm rule không phải sửa validator; mỗi rule test riêng |
| `Scorer` | **Strategy**: mỗi thành phần điểm là một `ScoreComponent` thuần, có trọng số từ config | Test từng thành phần, đổi trọng số không sửa code |
| `HardFilter` | Danh sách `Predicate`-style rule, mỗi rule trả `FilterReason` | Có lý do bị loại để debug |
| Thời tiết, kho đối tác | **Port & Adapter**: interface `WeatherProvider`, `PartnerInventoryProvider`; adapter `OpenMeteoClient`, `MockInventoryProvider` | Đổi nhà cung cấp, test bằng stub không gọi mạng |
| Gọi LLM | **Adapter mỏng** (`PlannerClient`, `AskService`…) quanh `ChatClient`, luôn có timeout + log | Không để `ChatClient` rải khắp code |
| Planner | **Template workflow** cố định trong `PlannerOrchestrator`: filter → score → evidence → plan → validate/repair/fallback → assemble | Đúng nguyên tắc "workflow cho tạo lịch" |
| Fallback | **Null-object / Fallback strategy**: `GreedyFallbackPlanner` cùng interface với planner LLM | Luôn trả được kết quả hợp lệ |
| DTO, value object | **Immutable record**; thay đổi bằng method trả bản sao (`TripRequest.apply(change)`) | Không có state ẩn |
| Prompt | **Template** file `.st` có version, nạp qua `PromptLoader` | Đổi prompt không sửa Java |

**Quy tắc code:**
- Inject bằng constructor, field `private final`. Không có `@Autowired` trên field (trừ trong test).
- Không dùng Lombok. Record và IDE đủ dùng.
- Không trả `null` cho collection; trả `List.of()`. Dùng `Optional` cho kết quả tìm một phần tử, không dùng làm field hay tham số.
- Không bắt `Exception` chung trong service. Chỉ bắt lỗi cụ thể rồi đổi sang `ApiException(ErrorCode…)`.
- Không có "magic number": ngưỡng, trọng số, timeout nằm trong `TravelPilotProperties`.
- Class công khai có Javadoc 1 dòng nói nó làm gì. Comment giải thích **vì sao**, không lặp lại code.
- Hàm dài quá ~40 dòng hoặc lồng quá 3 cấp thì tách ra.
- Đặt tên: class danh từ (`BudgetCalculator`), method động từ (`calculate`), boolean bắt đầu bằng `is/has/can`. Test đặt tên theo hành vi (`blankMessageIsRejectedWithoutCallingModel`).
- Format: 4 dấu cách, dòng ≤ 120 ký tự, import không dùng `*`.

## Cấu trúc và pattern (frontend)

- Thư mục theo tính năng: `src/features/<feature>/` chứa component, hook và type riêng; `src/components/` chỉ chứa component dùng chung; `src/api/` chỉ chứa hàm gọi HTTP.
- Component chỉ lo hiển thị. Gọi API qua **custom hook** dùng TanStack Query (`useCreateTrip`, `useTrip`), không `fetch` trực tiếp trong component.
- Function component + hook, không dùng class component. Props có type rõ ràng, không `any`.
- Logic thuần (format giá, tính phần trăm ngân sách) tách ra file `.ts` riêng để test bằng Vitest.
- Tailwind cho style; không dùng inline style trừ giá trị động.

## Lỗi

- `@RestControllerAdvice` trả về `ProblemDetail`, có thêm property `code` (enum `ErrorCode`) và `requestId`.
- Không trả stacktrace ra ngoài. Message cho người dùng phải ngắn gọn.
- Lỗi provider phải map về mã của mình: timeout thành `LLM_TIMEOUT` (504), 4xx/5xx từ provider thành `LLM_ERROR` (502).
- Chỉ retry khi gặp 429, 5xx hoặc timeout: tối đa 2 lần, backoff có jitter. Không retry lỗi 400 hay lỗi schema, vì phần đó đã có repair loop.

## Test

| Loại | Công cụ | Ghi chú |
|---|---|---|
| Unit | JUnit 5 + AssertJ | Mỗi rule validator, mỗi thành phần điểm, OpeningHours (duyệt mọi chuỗi trong catalog), BudgetCalculator (số liệu tính tay) |
| Integration | `@SpringBootTest` + Testcontainers `pgvector/pgvector:pg16` + `@ServiceConnection` | SeedLoader idempotent, ingest idempotent, repository, MockMvc cho API |
| AI giả | `StubChatModel` (trả lời lần lượt theo hàng đợi), `StubEmbeddingModel` (vector tất định theo hash của text) | Khai báo trong `@TestConfiguration` với `@Primary`. **`./mvnw test` phải xanh khi không có API key và không có mạng.** |
| Eval (gọi thật) | `@Tag("eval")` + `@EnabledIfEnvironmentVariable(named = "AI_API_KEY", matches = ".+")` | Loại khỏi build mặc định, chạy bằng `./mvnw test -Dgroups=eval` |
| Frontend | `npm run lint`, `npm run build` (bắt buộc), Vitest cho các hàm format giá và budget (khuyến nghị) | |

- Dữ liệu test nên dùng ID thật trong catalog (ví dụ `danang-way-359114064`) để test kiểm luôn dữ liệu.
- Khi thêm một rule hay thành phần mới, viết test trước hoặc ngay cùng lúc.

## Bảo mật (OWASP LLM top 10)

- Key nằm trong `.env`, không commit. Repo có `.env.example` với các biến `AI_API_KEY`, `DB_URL`, `DB_USER`, `DB_PASSWORD`, `GUEST_TOKEN_SECRET`.
- Prompt không chứa bí mật nào. Ràng buộc quan trọng phải được kiểm ở **code** (validator, filter), không chỉ dặn trong prompt.
- Bọc dữ liệu đưa vào prompt trong thẻ (`<candidates>`, `<evidence>`, `<context>`, `<notes>`), và ghi rõ trong prompt rằng đó là dữ liệu.
- Kiểm tra quyền sở hữu: trip của guest khác trả về 404, không trả 403.
- InputGuard:
  - Tin nhắn tối đa 2000 ký tự, mỗi note tối đa 200 ký tự.
  - Bỏ ký tự điều khiển.
  - Request body tối đa 32KB.
  - Đặt `max tokens` cho output.
- Rate limit theo guest token: 10 lần tạo lịch mỗi giờ, 60 tin nhắn mỗi giờ, và một trần tổng mỗi ngày. Vượt thì trả 429 `RATE_LIMITED`.
- Tool chỉ đọc, có allowlist, validate tham số, tối đa 3 vòng.
- Frontend render text thuần, không dùng `dangerouslySetInnerHTML`.
- Endpoint `/internal/**` chỉ bật ở profile dev.
- 6 test tấn công (chạy trong CI bằng stub):
  - T1: lộ prompt.
  - T2: chunk knowledge độc (fixture).
  - T3: truy cập trip của guest khác.
  - T4: ép model gọi tool lạ.
  - T5: output chứa `<script>`.
  - T6: spam, hoặc input quá lớn.

## Log và metric

- `RequestIdFilter` đặt `requestId` vào MDC và trả lại qua header `X-Request-Id`. Log ở dạng JSON có cấu trúc.
- Mỗi lần gọi LLM ghi một dòng `ai_call_logs`. Usage (token) lấy từ `ChatResponse.getMetadata().getUsage()`, dùng `.call().responseEntity(...)` để có cả entity lẫn metadata.
- Micrometer:
  - `tp.plan.latency`, gắn tag `step` (filter / retrieve / llm / validate / assemble).
  - `tp.plan.repairs`, `tp.plan.fallback`, `tp.llm.tokens`, `tp.tool.latency`, `tp.ratelimit.rejected`.
- Không log PII hay nội dung tin nhắn đầy đủ. `guestId` phải hash trước khi ghi log.

## Frontend

- Gọi API qua `src/api/client.ts`: hàm fetch có kiểu, parse `ProblemDetail`, và map `code` sang thông báo tiếng Việt kèm `requestId`.
- Type lấy từ `src/types/api.ts`, viết tay theo DTO hoặc sinh bằng springdoc + openapi-typescript. Không dùng `any`.
- Trạng thái mỗi màn hình dùng union type `idle | loading | error | success`.
- Hiển thị giá:
  - Định dạng `Intl.NumberFormat('vi-VN')` + "đ".
  - Giá ước tính hiển thị "~", tooltip là `priceNote`.
  - Có `verifyAt` thì hiện "Giá cập nhật dd/MM/yyyy".
  - Giá 0 hiện "Miễn phí".
- Footer luôn có dòng ghi công dữ liệu (xem 01-dataset.md §9).
- Phải dùng được ở độ rộng 375px.

## Git: commit thường xuyên

- **Commit nhỏ và thường xuyên**: ít nhất một commit cho mỗi task, và commit ngay khi đạt một bước chạy được (test xanh). Không gom nhiều task vào một commit, không để thay đổi chưa commit qua ngày.
- **Mỗi commit phải build được và test xanh** (`cd be && ./mvnw test`, `cd fe && npm run lint && npm run build` với phần bị ảnh hưởng). Không commit code đang hỏng.
- Message theo Conventional Commits, tiếng Anh, kèm ID task:
  - `feat(planner): T2.13 add PlanValidator with 12 rules`
  - `fix(catalog): T1.18 make SeedLoader idempotent`
  - Loại: `feat`, `fix`, `test`, `refactor`, `docs`, `chore`, `build`, `ci`. Scope là tên tính năng (`be`, `fe`, `catalog`, `planner`, `plan`…).
- Đánh `[x]` trong `PLAN_CHECKLIST.md` ở **cùng commit** hoàn thành task đó.
- Không commit: `.env`, key, file build (`target/`, `dist/`, `node_modules/`), file sửa trong `data/`.
- Làm trên `main` ở giai đoạn đầu; khi có CI (T9.05) thì làm theo nhánh `feat/<task-id>-<tên>` và chỉ merge khi CI xanh.
- Mỗi quyết định kiến trúc có một ADR trong `plan/adr/NNN-title.md`, 10–15 dòng, gồm các mục Context / Options / Decision / Consequences.
