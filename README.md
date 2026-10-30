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
| POST | `/api/v1/ai/chat` | Walking-skeleton chat (to be removed in Phase 6) |
| POST/GET | `/internal/ingest`, `/internal/search?q=&dest=` | Dev profile only |

## Data attribution

Map data © OpenStreetMap contributors (ODbL). Guide content from Wikivoyage and Wikipedia (CC BY-SA 4.0). See [data/ATTRIBUTION.md](data/ATTRIBUTION.md).
