# ADR-001: Spring Boot 4.1 + Spring AI 2.0, Google Gemini as the only provider

- Status: Accepted
- Date: 2026-10-30

## Context

The first plan targeted Spring Boot 3.5 + Spring AI 1.1.x to avoid migration risk. When the backend was created
(T0.03) no code existed yet, Spring AI 2.0.2 was the current GA release, and its auto-configuration is built
against Spring Boot 4.1.2 (checked in the published pom). Spring AI 1.1.x is the maintenance line.

## Options

1. Boot 3.5 + Spring AI 1.1.8: most tutorials and answers online target it; no new major version to learn.
2. Boot 4.1.2 + Spring AI 2.0.2: current versions, Jackson 3, modular starters; fewer examples online.

Provider: OpenAI or Google Gemini. Gemini (AI Studio key) has a free tier and a multilingual embedding model
that handles Vietnamese.

## Decision

Option 2: Boot 4.1.2 + Spring AI 2.0.2 with `spring-ai-starter-model-google-genai` (chat, default
`gemini-2.5-flash`) and `spring-ai-starter-model-google-genai-embedding` (`gemini-embedding-001`, 768 dims).
Model names are overridable via `AI_CHAT_MODEL` / `AI_EMBEDDING_MODEL`.

## Consequences

- No migration later; the project shows current-version skills.
- Boot 4 renamed starters and test slices (`spring-boot-starter-webmvc`, `-webmvc-test`, `-flyway`) and uses
  Jackson 3 (`tools.jackson.*`), Testcontainers 2 (`testcontainers-postgresql`). Code samples written for
  Boot 3 need adapting.
- Spring AI 2.0 property names were verified from the jar metadata; `*.options.*` keys are deprecated.
- Embedding dimension 768 is fixed in two places (embedding model + pgvector); changing it means re-ingesting.
- On the company network the internal Nexus lacks Spring AI artifacts, so the build uses Maven Central through
  the proxy (`-s ~/.m2/settings-central.xml`); the repo itself stays registry-neutral.
