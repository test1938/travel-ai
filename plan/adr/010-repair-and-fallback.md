# ADR-010: Validate, repair at most twice, then a deterministic fallback

- Status: Accepted
- Date: 2026-10-30
- Note: the checklist called this ADR-007; that number was already taken by the bounded tool loop.

## Context

Even with JSON mode and candidate ids, a draft can break hard rules: unknown ids, over budget, an activity for a
child who is too young, too many activities for the pace, closed at the planned time, a locked day changed. The
model can also time out or be unavailable. The traveler must always get a valid plan within about 40 seconds.

## Options

1. Return the model's draft as is, with warnings.
2. Retry until valid: unbounded cost and latency.
3. Validate with fixed rules, send the exact violations back for a bounded number of repairs, and if it is still
   invalid (or the model fails), build the plan with a deterministic planner.

## Decision

Option 3. `PlanValidator` checks 14 rules with stable codes; `RepairLoop` sends the hard violations to the model
(`repair-v1`) at most `travelpilot.planner.max-repairs` = 2 times; then `GreedyFallbackPlanner` fills the days
from the scored candidates with the same scheduling rules (`DaySchedule`). The plan says when the fallback was
used (`FALLBACK_USED`, and `NOTES_NOT_APPLIED` when the traveler's notes could not be read).

## Consequences

- Every returned plan passes the hard rules; worst case is 3 model calls per plan.
- Repair rate and fallback rate are first-class metrics (`tp.plan.repairs`, `tp.plan.fallback`, eval report).
- The fallback is a product in its own right and needs its own eval: the offline eval found a place it could
  never schedule (plan/eval-notes.md).
