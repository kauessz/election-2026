CREATE TABLE poll_results (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(), poll_id UUID NOT NULL REFERENCES polls(id) ON DELETE CASCADE,
    candidate_id UUID NOT NULL REFERENCES candidates(id),
    original_percentage NUMERIC(7,4) NOT NULL CHECK (original_percentage BETWEEN 0 AND 100),
    normalized_percentage NUMERIC(7,4) NOT NULL CHECK (normalized_percentage BETWEEN 0 AND 100),
    CONSTRAINT poll_results_poll_candidate_unique UNIQUE(poll_id, candidate_id)
);
CREATE INDEX poll_results_candidate_idx ON poll_results(candidate_id);
