-- One row per model call (after retries) and per executed chat tool. No prompt or answer text: only what is
-- needed to debug, measure latency and estimate cost (plan/security-notes.md, LLM02).
CREATE TABLE ai_call_logs (
    id             BIGSERIAL PRIMARY KEY,
    request_id     TEXT,
    operation      TEXT        NOT NULL,
    model          TEXT,
    prompt_version TEXT,
    tokens_in      INTEGER     NOT NULL DEFAULT 0,
    tokens_out     INTEGER     NOT NULL DEFAULT 0,
    latency_ms     INTEGER     NOT NULL,
    attempts       SMALLINT    NOT NULL DEFAULT 1,
    status         TEXT        NOT NULL,
    error_code     TEXT,
    retrieved_ids  TEXT[]      NOT NULL DEFAULT '{}',
    tool_calls     TEXT[]      NOT NULL DEFAULT '{}',
    cost_usd       NUMERIC(12, 8),
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ai_call_logs_status_chk CHECK (status IN ('OK', 'TIMEOUT', 'ERROR', 'REJECTED'))
);

CREATE INDEX ai_call_logs_created_idx ON ai_call_logs (created_at);
CREATE INDEX ai_call_logs_request_idx ON ai_call_logs (request_id);
