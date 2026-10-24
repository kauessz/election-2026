-- Isolated observational archive. No references to polls, candidates, performance or snapshots.
CREATE TABLE historical_polls (
    id UUID PRIMARY KEY,
    election_key VARCHAR(100) NOT NULL,
    round INTEGER NOT NULL CHECK (round IN (1, 2)),
    pollster_id UUID NOT NULL REFERENCES pollsters(id),
    registration_number VARCHAR(100),
    identity_key VARCHAR(200) NOT NULL,
    field_start DATE NOT NULL,
    field_end DATE NOT NULL CHECK (field_end >= field_start),
    published_at TIMESTAMPTZ NOT NULL,
    sample_size INTEGER CHECK (sample_size > 0),
    margin_of_error NUMERIC(7,4) CHECK (margin_of_error > 0 AND margin_of_error <= 100),
    methodology VARCHAR(5000),
    source_url VARCHAR(2000) NOT NULL CHECK (btrim(source_url) <> ''),
    vote_basis VARCHAR(10) NOT NULL CHECK (vote_basis IN ('TOTAL', 'VALID')),
    normalization VARCHAR(20) NOT NULL CHECK (normalization IN ('NONE', 'VALID_VOTES')),
    complete_candidate_set BOOLEAN NOT NULL,
    expected_candidate_keys JSONB NOT NULL CHECK (jsonb_typeof(expected_candidate_keys) = 'array'),
    notes VARCHAR(5000),
    content_hash VARCHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    UNIQUE (election_key, round, pollster_id, identity_key)
);
CREATE INDEX historical_polls_publication_idx ON historical_polls(election_key, round, published_at, id);
CREATE INDEX historical_polls_pollster_idx ON historical_polls(pollster_id);

CREATE TABLE historical_poll_results (
    id UUID PRIMARY KEY,
    historical_poll_id UUID NOT NULL REFERENCES historical_polls(id) ON DELETE CASCADE,
    candidate_key VARCHAR(100) NOT NULL CHECK (candidate_key ~ '^[a-z0-9]+(-[a-z0-9]+)*$'),
    candidate_name VARCHAR(200) NOT NULL CHECK (btrim(candidate_name) <> ''),
    display_name VARCHAR(200),
    party VARCHAR(50),
    ballot_number INTEGER CHECK (ballot_number BETWEEN 1 AND 99),
    original_percentage NUMERIC(7,4) NOT NULL CHECK (original_percentage BETWEEN 0 AND 100),
    normalized_percentage NUMERIC(7,4) CHECK (normalized_percentage BETWEEN 0 AND 100),
    UNIQUE (historical_poll_id, candidate_key)
);
CREATE UNIQUE INDEX historical_results_ballot_idx ON historical_poll_results(historical_poll_id, ballot_number)
    WHERE ballot_number IS NOT NULL;
