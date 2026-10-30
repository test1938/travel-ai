# ADR-009: Destinations, curated catalog and budget scope

- Status: Accepted
- Date: 2026-10-30 (summarises the dataset ADR of 2026-10-12)

## Context

The original plan used Da Nang, Hoi An and Phu Quoc. While building the dataset: Hoi An became part of Da Nang
city after the 2025 administrative merger; Sun Group runs large products in Hanoi, Da Nang (Ba Na Hills) and
Ha Long; OSM returns thousands of noisy candidates (Hanoi about 4,600) while the planner needs a small,
trustworthy set.

## Options

1. Keep Da Nang / Hoi An / Phu Quoc and plan from raw OSM data.
2. HANOI, DANANG (Hoi An as a day trip), QUANGNINH (Ha Long), with a hand-curated catalog.

## Decision

Option 2. `Destination` has exactly three values: `HANOI`, `DANANG`, `QUANGNINH`. The planner chooses only from
the curated catalog (45 / 34 / 22 places); the OSM pool is for search only. The budget covers **tickets and
planned meals only** (no hotel or transport), so demo budgets are smaller than in the old plan: family Da Nang
3 days = 6,000,000 VND; "reduce budget" scenario = 3,000,000 VND. The "couple in Hoi An" persona becomes
"couple in Hanoi".

## Consequences

- Places found closed or banned during verification (Asia Park, Helio Center, Hanoi train street, Bai Tho
  mountain, Sun World Ha Long Dragon Park) are excluded from the catalog.
- Ha Long Bay caves are merged into one "Ha Long Bay cruise" place to avoid double-counting the route ticket.
- Quang Ninh has little data (12 activities): long trips there must relax filters with warnings.
- The UI must say the budget excludes hotel and transport.
