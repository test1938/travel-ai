-- One conversation per trip. The full history is kept for the UI; only a short window is sent to the model.
CREATE TABLE conversations (
    id         UUID PRIMARY KEY,
    trip_id    UUID        NOT NULL UNIQUE REFERENCES trips (id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE messages (
    id              BIGSERIAL PRIMARY KEY,
    conversation_id UUID        NOT NULL REFERENCES conversations (id) ON DELETE CASCADE,
    role            TEXT        NOT NULL,
    content         TEXT        NOT NULL,
    intent          TEXT,
    trip_version    INTEGER,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT messages_role_chk CHECK (role IN ('USER', 'ASSISTANT'))
);

CREATE INDEX messages_conversation_idx ON messages (conversation_id, id);
