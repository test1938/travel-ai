# ADR-007: Bounded tool loop for chat tools

- Status: Accepted
- Date: 2026-10-30

## Context

Phase 5 adds three read-only chat tools (`getWeatherForecast`, `searchPlaces`, `checkAvailability`,
plan/03-ai-pipeline.md §8). Requirements: validate every argument, at most 3 tool rounds per message, log each
call, never let a tool failure break the chat. Spring AI 2.0 can run tools natively (`@Tool` callbacks passed
to `ChatClient`), but the model then executes tools inside the provider call: the round limit, argument
rejection messages and per-call logging depend on provider internals that changed between 1.x and 2.0, and
Gemini does not allow function calling together with `responseMimeType=application/json`.

## Options

1. Native function calling through `ChatClient.tools(...)`.
2. Own loop: the model returns the calls it wants as JSON (`ToolPlan`), the backend validates and runs them
   through the same `@Tool` callbacks (`ToolCallbacks.from(travelTools)`), then feeds results back.

## Decision

Option 2 (`ToolLoop`). Tool names, descriptions and argument schemas still come from the `@Tool`/`@ToolParam`
annotations, so moving to native calling later only changes the loop, not the tools. Limits: 3 rounds × 3 calls.
A keyword gate (accent-insensitive: weather, availability, search words) skips the extra model call for ordinary
questions. Successful results become passages `[ev-n]` (doc type `TOOL`) placed before retrieved knowledge,
so answers cite them like any other source.

## Consequences

- Same "model chooses, backend computes" rule as the planner; provider-independent and fully testable with the
  stub model (`ToolLoopTest`).
- One extra model call for questions that pass the gate; questions that need a tool but use none of the gate
  words are answered from the knowledge base only.
- Calls are logged now (`tool_call name=… args=… status=… latencyMs=…`); they move to `ai_call_logs.tool_calls`
  with the V5 table in T7.01.
