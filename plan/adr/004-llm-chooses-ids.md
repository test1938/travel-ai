# ADR-004: The LLM only chooses candidate ids; the backend fills every fact

- Status: Accepted
- Date: 2026-10-30

## Context

Travel plans contain facts people act on: prices, opening hours, minimum ages, travel times. Language models
invent plausible values, round numbers wrongly and mix up places. A wrong ticket price or an 18+ bar in a family
plan is worse than a dull plan.

## Options

1. Let the model write the whole itinerary (names, times, prices) and check it afterwards.
2. Give the model a scored candidate list with ids and let it return only structure: which id goes to which day
   and slot, plus a short reason and the evidence ids it used. The backend looks everything else up.

## Decision

Option 2. `TripPlanDraft` contains only ids, slots, reasons and evidence ids. The validator rejects any id that
is not a candidate (`UNKNOWN_PLACE`), and the assembler takes names, prices, hours and coordinates from the
catalog. The same rule applies in chat: change requests are sanitised to known ids and allowed values.

## Consequences

- No invented places or prices can reach the traveler; prompt injection cannot add a place outside the candidates
  (attack test T2).
- The quality of a plan is bounded by the catalog and the scorer: the model cannot suggest a great place we do not
  have.
- Reasons are still model text; their groundedness is measured by the LLM judge (judge-v1), not guaranteed.
