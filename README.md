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
cd be && ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev     # http://localhost:8080/actuator/health
cd fe && npm install && npm run dev                                 # http://localhost:5173
```

Tests run offline (no API key, no network). Database tests need Docker and are skipped without it:

```bash
cd be && ./mvnw test
cd fe && npm run lint && npm run build
```

## Data attribution

Map data © OpenStreetMap contributors (ODbL). Guide content from Wikivoyage and Wikipedia (CC BY-SA 4.0). See [data/ATTRIBUTION.md](data/ATTRIBUTION.md).
