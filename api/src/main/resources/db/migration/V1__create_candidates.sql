CREATE TABLE candidates (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(), name VARCHAR(200) NOT NULL CHECK (btrim(name) <> ''),
    display_name VARCHAR(200) NOT NULL CHECK (btrim(display_name) <> ''), party VARCHAR(50) NOT NULL CHECK (btrim(party) <> ''),
    ballot_number INTEGER NOT NULL CHECK (ballot_number > 0), active BOOLEAN NOT NULL DEFAULT TRUE, created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP, updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
