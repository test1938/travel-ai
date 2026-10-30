# ADR-011: One switch between Gemini and OpenAI

- Status: Accepted
- Date: 2026-10-30

## Context

The app was built on Gemini 2.5 Flash. The owner also wants to run it on OpenAI (one key is easier to get than
the other, prices and free tiers change, and comparing two models in the eval is useful). Requirements: the
cheapest OpenAI model that still follows the JSON contract, a switch that needs no code change, and no key for
the provider that is not used.

Facts found while wiring it (Spring AI 2.0.2):
- `spring.ai.model.chat` selects the chat auto-configuration. Embeddings are selected by
  `spring.ai.model.embedding.text` for Gemini but by `spring.ai.model.embedding` for OpenAI, and OpenAI's matches
  when the key is missing: without both keys, a Gemini-only setup failed for lack of an OpenAI key.
- The Gemini embedding *connection* is always created and throws without a key, whatever the provider.
- The OpenAI Java SDK retries 2 times by default; our `LlmGateway` already retries 429/5xx/timeouts.
- OpenAI reasoning models (gpt-5 family, o-series) reject a custom temperature and `max_tokens`.
- Vectors of two embedding models are not comparable, even at the same dimension: switching silently would make
  retrieval return noise, with no error.

## Options

1. Two builds or two Spring profiles with copied configuration.
2. Our own `LlmClient` abstraction over both SDKs.
3. Spring AI's model-selection properties driven by one env variable, plus small fixes for the facts above.

## Decision

Option 3. `AI_PROVIDER=google-genai|openai` (and optionally `AI_EMBEDDING_PROVIDER`) sets both selection keys.
`AiModels` resolves provider and model names and fails at startup when the chosen provider has no key.
`AiConfig` gives each provider its native options: JSON mode (`responseMimeType` / `response_format=json_object`),
an output cap (`maxOutputTokens` / `max_completion_tokens`), and thinking budget or reasoning effort.
`UnusedGeminiEmbeddingConfig` replaces the Gemini embedding connection with an inert one when Gemini does not
embed. The SDK retries are off. Every ingested row records `embeddingId`; `IngestRunner` re-embeds once when it
differs from the configured model.

Default OpenAI models: `gpt-4.1-mini` for chat and `text-embedding-3-small` at 768 dimensions.
- gpt-4.1-mini is a non-reasoning model. It is fast, accepts a temperature, and follows JSON well.
- It costs $0.40 / $1.60 per 1M input/output tokens, so a 3-day plan is about $0.004, slightly below Gemini 2.5
  Flash.
- gpt-4.1-nano ($0.10 / $0.40) is the budget option. The validator and repair loop catch its mistakes.
- gpt-5-mini works too, but its hidden reasoning tokens bill as output.

## Consequences

- Switching is one line in `.env` plus a restart; the first start after an embedding switch takes about a minute.
- Prompts are shared, so quality can differ per provider: run `EvalRunner` with `-Deval.label=openai-...` and
  compare reports before switching a deployment.
- Prices in `travelpilot.pricing` come from third-party tables (October 2026); check the official pages.
- Adding a third provider means one more branch in `AiConfig.options` and `RetryPolicy`, and its starter.
