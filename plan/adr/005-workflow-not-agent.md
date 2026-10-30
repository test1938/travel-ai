# ADR-005: A fixed workflow, not an autonomous agent

- Status: Accepted
- Date: 2026-10-30

## Context

Creating a plan always needs the same steps: weather, filter and score candidates, retrieve evidence, draft,
validate, repair, assemble. An agent that decides which tools to call and when could do the same, but its number
of model calls, latency and cost vary per request, and failures are hard to reproduce and test.

## Options

1. Agent loop: the model plans its own steps and calls tools until it decides it is done.
2. Fixed workflow in code (`PlannerOrchestrator`), with the model used at two well-defined points: drafting (and
   repairing) the plan, and chat tasks (intent, change extraction, answers, bounded tool selection).

## Decision

Option 2. The orchestrator runs the steps in order and times each one (`tp.plan.latency{step}`). The model gets
one job per call with a versioned prompt and structured output. Where the model may choose tools (chat
questions), the loop is bounded by the backend (ADR-007).

## Consequences

- Predictable cost and latency: at most 1 draft + 2 repairs per plan; every step is unit-testable offline.
- Each step can fail on its own (no weather, no evidence, model down) without stopping the plan.
- Less flexible: a new capability means a code change, not a new tool for the agent. That is acceptable for a
  product with a narrow scope.
