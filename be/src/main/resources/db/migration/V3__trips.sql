-- A trip belongs to one guest; every change creates a new immutable version (compare / undo / eval data).
CREATE TABLE trips (
    id              UUID PRIMARY KEY,
    guest_id        TEXT        NOT NULL,
    destination     TEXT        NOT NULL,
    request_json    JSONB       NOT NULL,
    current_version INTEGER     NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX trips_guest_idx ON trips (guest_id, created_at DESC);

CREATE TABLE trip_versions (
    trip_id        UUID        NOT NULL REFERENCES trips (id) ON DELETE CASCADE,
    version        INTEGER     NOT NULL,
    plan_json      JSONB       NOT NULL,
    request_json   JSONB       NOT NULL,
    change_summary TEXT,
    prompt_version TEXT,
    model          TEXT,
    tokens_in      INTEGER     NOT NULL DEFAULT 0,
    tokens_out     INTEGER     NOT NULL DEFAULT 0,
    latency_ms     BIGINT      NOT NULL DEFAULT 0,
    repaired       BOOLEAN     NOT NULL DEFAULT FALSE,
    fallback       BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (trip_id, version)
);
