CREATE TABLE polls (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(), pollster_id UUID NOT NULL REFERENCES pollsters(id),
    tse_registration_number VARCHAR(100), field_start DATE NOT NULL, field_end DATE NOT NULL,
    published_at TIMESTAMPTZ NOT NULL, sample_size INTEGER CHECK (sample_size > 0),
    margin_of_error NUMERIC(7,4) CHECK (margin_of_error > 0 AND margin_of_error <= 100), methodology VARCHAR(5000),
    source_url VARCHAR(2000) NOT NULL CHECK (btrim(source_url) <> ''), vote_basis VARCHAR(10) NOT NULL CHECK (vote_basis IN ('TOTAL', 'VALID')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP, updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP, CONSTRAINT polls_field_dates CHECK (field_end >= field_start)
);
CREATE INDEX polls_latest_idx ON polls(pollster_id, published_at DESC, field_end DESC, created_at DESC, id DESC);
CREATE INDEX polls_publication_idx ON polls(published_at DESC, field_end DESC, created_at DESC, id DESC);
