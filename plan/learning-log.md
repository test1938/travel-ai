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

## 2026-10-30: Phase 2 (recommendation + planner)

- **Snapshot tests catch product bugs, not just regressions.** The persona snapshot showed Bà Nà (the only
  THEME-PARK place in Da Nang) outside the family's candidates: its budget score is 0 (2.8M vs a 600k fair
  share per activity) and it is far and long. Instead of tuning weights to pass a test, candidate selection now
  keeps the best place for every requested preference, and the fallback schedules those "must-see" places first.
- **One timeline for everyone.** `DaySchedule` (start times, 30-minute travel, meals first in afternoon/evening,
  long visits) is used by the validator, the greedy fallback and the assembler, so a plan the validator accepts
  is exactly the plan the user sees.
- **Dry run of the fallback with the LLM switched off** (read like a user, not like a test) found four more
  issues: morning visits running past noon (slot capacity ignored travel time), a museum scheduled at 20:20
  after closing (only the day was checked, not the time), a yogurt shop as dinner and a café as lunch, and lunch
  pushing afternoon visits past closing time. Fixes: capacity = durations + travel (validator and fallback share
  `activityMinutes`), the fallback simulates the whole day with `DaySchedule` before adding anything, cafés are
  last choice for meals. Each became a regression test.
- **Spring 7 method validation.** With a constraint on a header *and* `@Valid` on the body, errors arrive as
  `HandlerMethodValidationException`, not `MethodArgumentNotValidException`; the handler now extracts field
  errors from both.
- **Flyway numbering follows the real order** (V3 trips in Phase 2), not the order in the original plan.
- **Still to do with a real key:** run the three personas through Gemini (T2.12, T2.17, T2.19) and record
  first-try schema validity, repairs and latency.

## 2026-10-30: Phase 4 (chat + partial re-planning)

- **Constraints live in the database, not in the chat.** Each turn rebuilds the context from the trip's current
  request and plan plus only the last 6 messages; a three-turn test proves the budget changed in turn 1 is still
  seen in turn 3 even though the earlier messages could have dropped out of the window.
- **Two kinds of model output, two kinds of safety net.** Intent routing falls back to an accent-insensitive
  keyword heuristic when the model fails, so chat never dies on a provider error; small talk and out-of-scope
  messages get fixed replies (no second model call, nothing to leak).
- **Sanitise what the model extracts.** The change extractor drops day numbers outside the trip, place ids that
  are not in the plan, preferences outside the form list and non-positive budgets before anything is applied.
- **"Only day 2 changes" needs more than a prompt.** Locked days go to the planner as data and are checked by
  `LOCKED_DAY_CHANGED`, but the guarantee comes from the merger: locked days are copied back from the previous
  version (same times, reasons and citations, citation ids renamed `v1-ev-k`), and the budget is recomputed.
- **Prompt changes bump versions** (ask-v1 → ask-v2 for unverified child-age facts and chat history), so logs
  and eval results stay comparable.
