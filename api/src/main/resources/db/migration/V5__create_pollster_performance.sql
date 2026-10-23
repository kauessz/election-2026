CREATE TABLE pollster_performance (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(), pollster_id UUID NOT NULL REFERENCES pollsters(id),
    election_key VARCHAR(100) NOT NULL CHECK (btrim(election_key) <> ''), round INTEGER NOT NULL CHECK (round IN (1,2)),
    candidate_error NUMERIC(12,4) CHECK (candidate_error >= 0), spread_error NUMERIC(12,4) CHECK (spread_error >= 0),
    historical_score NUMERIC(12,4), created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP, updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pollster_performance_election_unique UNIQUE(pollster_id, election_key, round)
);
