-- Curated catalog (data/catalog_*.json), loaded by SeedLoader. Single source of truth for prices and hours.
CREATE TABLE places (
    id               TEXT PRIMARY KEY,
    destination      TEXT             NOT NULL,
    type             TEXT             NOT NULL,
    name             TEXT             NOT NULL,
    name_en          TEXT,
    description      TEXT             NOT NULL,
    curated          BOOLEAN          NOT NULL DEFAULT TRUE,
    osm_id           TEXT,
    tags             TEXT[]           NOT NULL DEFAULT '{}',
    est_cost_vnd     BIGINT           NOT NULL,
    child_cost_vnd   BIGINT           NOT NULL,
    price_level      TEXT             NOT NULL,
    price_note       TEXT             NOT NULL,
    price_source_url TEXT,
    verify_at        DATE,
    duration_min     INTEGER          NOT NULL,
    child_min_age    INTEGER          NOT NULL,
    indoor           BOOLEAN          NOT NULL,
    best_time        TEXT             NOT NULL,
    opening_hours    TEXT             NOT NULL,
    lat              DOUBLE PRECISION NOT NULL,
    lon              DOUBLE PRECISION NOT NULL,
    popularity       DOUBLE PRECISION NOT NULL,
    day_trip         BOOLEAN          NOT NULL,
    source_url       TEXT,
    estimated_fields TEXT[]           NOT NULL DEFAULT '{}',
    review_fields    TEXT[]           NOT NULL DEFAULT '{}',
    field_sources    JSONB            NOT NULL DEFAULT '{}'::jsonb,
    updated_at       TIMESTAMPTZ      NOT NULL DEFAULT now(),
    CONSTRAINT places_destination_chk CHECK (destination IN ('HANOI', 'DANANG', 'QUANGNINH')),
    CONSTRAINT places_type_chk CHECK (type IN ('ACTIVITY', 'FOOD')),
    CONSTRAINT places_cost_chk CHECK (est_cost_vnd >= 0 AND child_cost_vnd >= 0)
);

CREATE INDEX places_destination_type_idx ON places (destination, type);
CREATE INDEX places_tags_idx ON places USING GIN (tags);
