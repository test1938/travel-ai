# Learning log

One entry per working session: what I tried, what surprised me, what I would do differently.

## 2026-10-30: Phase 0 (skeleton)

- **Versions.** Switched from Boot 3.5 + Spring AI 1.1 to Boot 4.1.2 + Spring AI 2.0.2 before writing any code
  (ADR-001). Spring AI 2.0.2's own pom pins Boot 4.1.2, so the two must move together.
- **Spring AI 2.0 config keys changed.** `spring.ai.google.genai.chat.options.model` is deprecated in favour of
  `spring.ai.google.genai.chat.model` (same for temperature, embedding model and dimensions). Reading
  `META-INF/spring-configuration-metadata.json` inside the autoconfigure jar is the fastest way to be sure.
- **Boot 4 is modular.** `@WebMvcTest` now lives in `spring-boot-webmvc-test`
  (`org.springframework.boot.webmvc.test.autoconfigure`); Flyway needs `spring-boot-starter-flyway`;
  Testcontainers 2 renamed its artifacts (`testcontainers-postgresql`).
- **LLM timeout.** The Gemini SDK's own HTTP timeout is not exposed in the same way as RestClient-based
  providers, so the chat call runs on a virtual thread with `Future.get(timeout)`; this is provider-agnostic and
  easy to test with a delayed stub (504 `LLM_TIMEOUT`).
- **Company network.** The internal Nexus has no Spring AI artifacts and misses some npm packages (oxlint,
  rolldown plugins). Fix: a separate Maven settings file pointing to Central through the proxy, and
  `fe/.npmrc` pointing to registry.npmjs.org.
- **No Docker on the dev machine yet.** Database tests are written but skip themselves
  (`@Testcontainers(disabledWithoutDocker = true)`), so `./mvnw test` stays green offline. To do: install Docker
  and run them.

## 2026-10-30: Phase 1 (catalog + RAG)

- **Two record types for one place.** `PlaceJson` mirrors the file (every field nullable) and is validated;
  `Place` is the domain record with primitives. Missing fields fail at load time with file + element index,
  instead of becoming silent zeros in the planner.
- **OpeningHours.** Writing a small parser for the 60-odd strings actually in the catalog was quicker and more
  predictable than a general OSM library. Key semantics: ";" rules override per day (so a dated "off" closes the
  Mausoleum during renovation), ", " rules add hours, ranges can wrap the year or pass midnight.
- **NamedParameterJdbcTemplate expands `List` parameters into `?, ?, ?`** but passes `String[]` through as one
  JDBC array. Checked with jshell before relying on it for `text[]` columns and `= ANY(:ids)`.
- **Template injection by data.** One knowledge chunk contains `{`. `ChatClient.system(String)` /
  `.user(String)` treat text as a template, so prompts are rendered once by our PromptLoader and sent as
  `SystemMessage`/`UserMessage`. Also: StringTemplate writes the OS line separator, so rendered prompts are
  normalised to `\n` to keep token counts identical across OSes.
- **Offline RAG tests.** Spring AI's `SimpleVectorStore` evaluates the same filter expressions as pgvector, so
  destination filtering and idempotent ingestion are tested without Docker. Gotcha: `similarityThresholdAll()`
  still drops vectors with negative cosine similarity, so it cannot be used to count documents.
- **Still to measure:** real retrieval quality (hit@5) needs Docker + a Gemini key (`RetrievalEvalRunner`).
