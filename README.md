# TravelPilot AI

TravelPilot AI turns a vague travel request into a grounded, budget-checked, day-by-day itinerary that travelers can refine through conversation.

Work in progress. Plan: [plan/PLAN_CHECKLIST.md](plan/PLAN_CHECKLIST.md).

## Repository layout

| Folder | Content |
|---|---|
| `data/` | Processed dataset (catalog, RAG knowledge, golden set). Read-only. |
| `plan/` | Plan, architecture, conventions, ADRs |
| `be/` | Backend: Java 21, Spring Boot 4.1, Spring AI 2.0 (Google Gemini), PostgreSQL + pgvector |
| `fe/` | Frontend: React + TypeScript + Vite |

## Run locally

```bash
cp .env.example .env            # then fill AI_API_KEY (Google AI Studio key)
docker compose up -d            # Postgres 16 + pgvector
cd be && ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev -Dspring-boot.run.arguments=--ingest
# seeds the catalog (101 places), embeds the knowledge (401 chunks) once; drop --ingest on later runs
cd fe && npm install && npm run dev                                 # http://localhost:5173
```

Tests run offline (no API key, no network). Database tests need Docker and are skipped without it:

```bash
cd be && ./mvnw test
cd fe && npm run lint && npm run build
```

Retrieval eval on the golden set (real Gemini + Docker; writes `be/eval/results/retrieval-YYYYMMDD.md`):

```bash
cd be && ./mvnw test -Dgroups=eval -Dtest=RetrievalEvalRunner
```

## API so far

| Method | Path | Purpose |
|---|---|---|
| GET | `/api/v1/places?destination=&type=&tag=&page=&size=` | Browse the curated catalog (`tag=THEME-PARK`) |
| GET | `/api/v1/places/{id}` | One place |
| POST | `/api/v1/ask` | Grounded Q&A: `{destination, question}` → `{answer, sources[], noEvidence}` |
| POST | `/api/v1/guest` | Anonymous guest token (HS256 JWT, 7 days); send it as `Authorization: Bearer …`. With a valid token: renewed for the same guest |
| POST | `/api/v1/trips` | Plan a trip: `TripRequest` → `TripPlan` (version 1); owner = guest of the token, other guests get 404 |
| GET | `/api/v1/trips/{id}`, `/api/v1/trips/{id}/versions/{v}` | Read a trip or one version (other guests get 404) |
| POST/GET | `/api/v1/trips/{id}/messages` | Chat on a trip: ask (RAG with citations) or change it (partial re-plan → new version); GET = history |
| POST | `/api/v1/ai/chat` | Walking-skeleton chat, **`dev` profile only** (used by `/playground`) |
| POST/GET | `/internal/ingest`, `/internal/search?q=&dest=`, `/api/v1/trips/candidates` | Dev profile only |

## How a plan is made

1. Hard filter (destination, child age, price cap, opening days) → weighted score (6 components) → candidates.
2. Evidence passages for the top candidates (RAG, destination-filtered).
3. Gemini picks **only candidate ids** per day/slot with reasons (`planner-v1`, JSON mode).
4. 14 validation rules; up to 2 repairs with the exact violations; deterministic greedy fallback otherwise.
5. Backend fills names, prices, times (shared `DaySchedule`) and computes the budget. The model never does math.

## Data attribution

Map data © OpenStreetMap contributors (ODbL). Guide content from Wikivoyage and Wikipedia (CC BY-SA 4.0). See [data/ATTRIBUTION.md](data/ATTRIBUTION.md).
