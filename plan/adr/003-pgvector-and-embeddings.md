# ADR-003: pgvector in Postgres, gemini-embedding-001 at 768 dimensions

- Status: Accepted
- Date: 2026-10-30

## Context

The knowledge base is small (401 chunks over 3 destinations, from Wikivoyage, Wikipedia and hand-written tips).
Retrieval must filter by destination and document type. Trips, conversations and logs already live in Postgres.
pgvector's HNSW index supports at most 2,000 dimensions; `gemini-embedding-001` returns 3,072 by default and
supports smaller output sizes (Matryoshka).

## Options

1. A separate vector database (Qdrant, Pinecone): more features, another service and account.
2. pgvector in the same Postgres through Spring AI `PgVectorStore`: one database, SQL filters, HNSW index.
3. In-memory vectors only: no persistence, re-embedding on every start costs money.

## Decision

pgvector in the application database, HNSW index with cosine distance, `gemini-embedding-001` with
`dimensions: 768` (the same number in `spring.ai.vectorstore.pgvector.dimensions`). Destination and doc type are
metadata filters. Tests use Spring AI `SimpleVectorStore`, which evaluates the same filter expressions.

## Consequences

- One database to run, back up and deploy; the free tiers of managed Postgres (Neon, Supabase) include pgvector.
- 768 dimensions keep the index small; retrieval quality is measured by the golden set (hit@k, MRR), not assumed.
- Changing the embedding model or the dimensions means re-ingesting everything (`/internal/ingest`).
