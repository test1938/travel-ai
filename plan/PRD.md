# TravelPilot AI: PRD (one page)

## Problem

Planning a short trip in Vietnam means piecing together attractions, food, opening hours, ticket prices and
weather from blogs, maps and Facebook groups, then balancing budget, children's ages and pace by hand. Generic
chatbots invent places and prices, give no sources, and rewrite the whole plan when asked to change one day.

## Primary persona

A Vietnamese family (2 adults + a 6-year-old) planning 3 days in Da Nang with a fixed budget. Pain: not knowing
what suits the child, fear of overspending, rainy-day backups. Secondary: a couple in Hanoi (food + photos),
a group of friends in Ha Long (nature + adventure).

## Hypotheses

- H1: An itinerary built only from a verified catalog, with per-item reasons and sources, is trusted more than a
  free-form chatbot answer.
- H2: Computing the budget in code (not by the LLM) removes over-budget surprises.
- H3: Editing one day by chat ("day 2 is rainy, go indoors") without touching other days is the feature users
  value most after the first plan.

## Success metrics

| Type | Metric | Target |
|---|---|---|
| Product | Time to a usable plan | < 3 minutes |
| Product | Usefulness score from real users | ≥ 4/5 |
| AI | Final plans with only catalog IDs and correct totals | 100% |
| AI | Draft schema-valid on first try | ≥ 95% |
| AI | Retrieval hit@5 on the golden set | ≥ 80% |
| Engineering | p95 plan latency (no streaming) | < 20 s |
| Engineering | Clone to running demo | < 10 minutes |

## P0 scope

Form → structured day-by-day plan for 3 destinations; hard filters + weighted scoring; RAG with citations and
"no evidence" handling; LLM picks IDs → validator → repair → greedy fallback; backend budget; chat edits with
plan versions; weather + read-only tools; guest token, rate limit, input limits, 6 attack tests; 20-case eval;
structured logs; Docker Compose; README and demo video.

## Non-goals

Booking, payment or real tickets; live flight prices; multi-agent systems; fine-tuning; blockchain code (design
note only); native mobile app; social login; more than 3 destinations.

## Risks

LLM latency and cost (mitigate: small candidate table, timeouts, fallback); stale prices (show verify date,
"estimated" labels); thin data for Quang Ninh (relaxation with warnings).
