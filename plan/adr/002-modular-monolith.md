# ADR-002: One Spring Boot modular monolith

- Status: Accepted
- Date: 2026-10-30

## Context

One developer, a few weeks, a portfolio project that must run after one `git clone`. The domain has clear parts
(catalog, recommendation, planner, trip, conversation, knowledge/RAG, security, integrations) but they share one
database, one request flow and one deployment. Expected load is a handful of users.

## Options

1. Microservices (planner, chat, catalog, RAG as separate services): independent scaling, but network calls,
   distributed failures, several deployables and much more infrastructure to explain and run.
2. One deployable split into packages by feature, with dependencies pointing one way and ports for external
   systems (weather, inventory, model).

## Decision

One Spring Boot application, packages by feature (`catalog`, `recommendation`, `planner`, `trip`,
`conversation`, `knowledge`, `ask`, `tools`, `integration`, `security`, `common`). External systems sit behind
interfaces (`WeatherProvider`, `PartnerInventoryProvider`, `DraftPlanner`, `AiCallLogStore`) so they can be
replaced or stubbed.

## Consequences

- One `./mvnw verify`, one container, one database: the whole system runs offline in tests.
- Transactions are local (a trip and its first version are saved together).
- Package boundaries are a convention, not enforced by the build; if a part ever needs its own deployment, the
  ports are where to cut. ArchUnit or Spring Modulith could enforce the rules later.
