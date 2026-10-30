# ADR-006: Money is computed by the backend

- Status: Accepted
- Date: 2026-10-30

## Context

Budget is a hard constraint for travelers. A plan's cost depends on group composition (adult and child tickets),
free places, meals and estimated prices. Models are unreliable at arithmetic and at applying such rules
consistently.

## Options

1. Ask the model for a total and check it.
2. Compute every amount in code from catalog prices and the request.

## Decision

`BudgetCalculator` computes the total per item and per type from catalog prices (adult/child tickets, meals per
person) and flags `overBudget`. The model never sees a sum it has to produce; the prompt only gives prices so it
can choose sensibly, and the validator rejects drafts over the budget. Budget covers tickets and planned meals,
not hotels or transport (ADR-009).

## Consequences

- Totals are exact and testable with hand-calculated numbers.
- Estimated prices are marked (`costEstimated`, price disclaimer) instead of being presented as certain.
- When nothing fits the budget, the plan says so (warning) rather than hiding costs.
